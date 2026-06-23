package io.devnogari.gajaedeck.bridge

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EndpointsTest {
    @Test
    fun buildsDocumentedPaths() {
        val e = BridgeEndpoints("https://host:4077/", sessionId = "S1")
        assertEquals("https://host:4077/healthz", e.healthz)
        assertEquals("https://host:4077/v1/help", e.help)
        assertEquals("https://host:4077/v1/handshake", e.handshake)
        assertEquals("https://host:4077/v1/sessions/S1/events?last_seq=7", e.events(7))
        assertEquals("https://host:4077/v1/sessions/S1/commands", e.commands)
        assertEquals("https://host:4077/v1/sessions/S1/ui-responses/c9", e.uiResponse("c9"))
        assertEquals("https://host:4077/v1/sessions/S1/control:claim", e.controlClaim)
    }
}

class CommandCatalogTest {
    @Test
    fun fullCatalogFrozen() {
        assertEquals(38, CommandCatalog.commands.size)
        assertEquals(BridgeScope.PROMPT, CommandCatalog.scopeFor("prompt"))
        assertEquals(BridgeScope.BASH, CommandCatalog.scopeFor("bash"))
        assertEquals(BridgeScope.MESSAGE_READ, CommandCatalog.scopeFor("get_session_stats"))
        assertEquals(BridgeScope.ADMIN, CommandCatalog.scopeFor("handoff"))
        assertNull(CommandCatalog.scopeFor("nope"))
    }

    @Test
    fun scopeGating() {
        assertTrue(CommandCatalog.isAllowed("bash", setOf(BridgeScope.BASH)))
        assertFalse(CommandCatalog.isAllowed("bash", setOf(BridgeScope.PROMPT)))
        assertFalse(CommandCatalog.isAllowed("unknown_cmd", BridgeScope.entries.toSet()))
    }
}

class FrameParserTest {
    @Test
    fun parsesNdjson() {
        val body = """
            {"type":"ready","seq":1,"session_id":"S1","protocol_version":2}
            {"type":"event","seq":2,"frame_id":"f2"}
        """.trimIndent()
        val frames = BridgeStreamParser.parseAll(body)
        assertEquals(2, frames.size)
        assertEquals(BridgeFrameType.READY, frames[0].type)
        assertEquals(1L, frames[0].seq)
        assertEquals(2, frames[0].protocolVersion)
        assertEquals(BridgeFrameType.EVENT, frames[1].type)
        assertEquals("f2", frames[1].frameId)
    }

    @Test
    fun parsesSse() {
        val body = "data: {\"type\":\"permission_request\",\"seq\":5}\n\ndata: {\"type\":\"response\",\"seq\":6}\n\n"
        val frames = BridgeStreamParser.parseAll(body)
        assertEquals(2, frames.size)
        assertEquals(BridgeFrameType.PERMISSION_REQUEST, frames[0].type)
        assertEquals(5L, frames[0].seq)
        assertEquals(BridgeFrameType.RESPONSE, frames[1].type)
    }

    @Test
    fun unknownTypeIsForwardCompatible() {
        val frames = BridgeStreamParser.parseAll("""{"type":"future_frame","seq":9,"x":1}""")
        assertEquals(1, frames.size)
        assertEquals(BridgeFrameType.UNKNOWN, frames[0].type)
        assertEquals(9L, frames[0].seq)
        assertTrue(frames[0].raw.containsKey("x"))
    }

    @Test
    fun incrementalAcrossChunks() {
        val p = BridgeStreamParser()
        assertEquals(0, p.feed("{\"type\":\"event\",\"se").size)
        val frames = p.feed("q\":3}\n")
        assertEquals(1, frames.size)
        assertEquals(3L, frames[0].seq)
    }
}

class TimelineReducerTest {
    private fun frame(seq: Long?, type: BridgeFrameType = BridgeFrameType.EVENT, reason: String? = null): BridgeFrame {
        val json = buildString {
            append("{\"type\":\"${type.wire}\"")
            if (seq != null) append(",\"seq\":$seq")
            if (reason != null) append(",\"reason\":\"$reason\"")
            append("}")
        }
        return BridgeStreamParser.parseFrame(json)!!
    }

    @Test
    fun appliesInOrderAndIgnoresDuplicates() {
        val r = TimelineReducer()
        assertEquals(FrameDisposition.APPLIED, r.accept(frame(1)))
        assertEquals(FrameDisposition.APPLIED, r.accept(frame(2)))
        assertEquals(FrameDisposition.DUPLICATE, r.accept(frame(2)))
        assertEquals(2L, r.state.lastSeq)
        assertEquals(1, r.state.duplicates)
        assertFalse(r.state.desynced)
    }

    @Test
    fun gapMarksDesynced() {
        val r = TimelineReducer()
        r.accept(frame(1))
        assertEquals(FrameDisposition.GAP, r.accept(frame(5)))
        assertTrue(r.state.desynced)
    }

    @Test
    fun resetClearsTimeline() {
        val r = TimelineReducer(TimelineState(lastSeq = 10, desynced = true))
        assertEquals(FrameDisposition.RESET, r.accept(frame(0, BridgeFrameType.RESET, "replay_window_exceeded")))
        assertFalse(r.state.desynced)
        assertEquals(0L, r.state.lastSeq)
    }
}

class ConnectionMachineTest {
    @Test
    fun reconnectReplayCycle() {
        var m = ConnectionMachine(ConnectionState.CONNECTED_STREAMING)
        m = m.on(ConnectionEvent.StreamClosedEof)
        assertEquals(ConnectionState.BACKOFF_RECONNECTING, m.state)
        m = m.on(ConnectionEvent.StreamOpened)
        assertEquals(ConnectionState.REPLAYING, m.state)
        m = m.on(ConnectionEvent.ReplayComplete)
        assertEquals(ConnectionState.CONNECTED_STREAMING, m.state)
    }

    @Test
    fun replayWindowExceededRecovers() {
        var m = ConnectionMachine(ConnectionState.REPLAYING)
        m = m.on(ConnectionEvent.ReplayWindowExceeded)
        assertEquals(ConnectionState.DESYNCED, m.state)
        m = m.on(ConnectionEvent.SnapshotRecovered)
        assertEquals(ConnectionState.CONNECTED_STREAMING, m.state)
    }

    @Test
    fun authBlock() {
        val m = ConnectionMachine(ConnectionState.CONNECTED_STREAMING).on(ConnectionEvent.Blocked(BridgeErrorCode.UNAUTHORIZED))
        assertEquals(ConnectionState.AUTH_BLOCKED, m.state)
        assertTrue(m.isBlocked)
    }
}

class IdempotencyTest {
    @Test
    fun keysAreUniqueAndRetryReuses() {
        val keys = IdempotencyKeys()
        val a = keys.next()
        val b = keys.next()
        assertTrue(a != b)
        val w = PendingWrite(key = a, type = "prompt")
        assertEquals(a, w.retried().key)
        assertEquals(2, w.retried().attempt)
    }
}

class FakeTransportTest {
    @Test
    fun handshakeAndAllowedCommand() = runTest {
        val t = FakeBridgeTransport()
        val hs = t.handshake(
            BridgeHandshakeRequest(ProtocolVersionRange(1, 2), listOf("events"), listOf("message:read")),
        )
        assertTrue(hs is BridgeHandshakeResult.Accepted)
        val resp = t.postCommand("get_session_stats", idempotencyKey = "k1")
        assertTrue(resp.success)
        assertEquals(1, t.sentCommands.size)
    }

    @Test
    fun scopeDeniedThrows() = runTest {
        val t = FakeBridgeTransport(grantedScopes = setOf(BridgeScope.PROMPT))
        val ex = assertFailsWith<BridgeException> { t.postCommand("bash", idempotencyKey = "k2") }
        assertEquals(BridgeErrorCode.SCOPE_DENIED, ex.code)
        assertEquals("bash", ex.scope)
    }

    @Test
    fun idempotencyReplayReturnsCachedWithoutResend() = runTest {
        val t = FakeBridgeTransport()
        t.postCommand("prompt", idempotencyKey = "same")
        t.postCommand("prompt", idempotencyKey = "same")
        assertEquals(1, t.sentCommands.size)
    }

    @Test
    fun eventsResumeFromLastSeq() = runTest {
        val frames = listOf(
            BridgeStreamParser.parseFrame("""{"type":"event","seq":1}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"event","seq":2}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"event","seq":3}""")!!,
        )
        val t = FakeBridgeTransport(frames = frames)
        val got = t.events(lastSeq = 1).toList()
        assertEquals(listOf(2L, 3L), got.map { it.seq })
    }
}
