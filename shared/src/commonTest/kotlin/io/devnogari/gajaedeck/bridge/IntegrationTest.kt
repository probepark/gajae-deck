package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.ui.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G010: fake-bridge integration happy path + stress/replay matrix. */
class IntegrationTest {

    @Test
    fun pairConnectPromptEventPermissionGateComplete() = runTest {
        val frames = listOfNotNull(
            BridgeStreamParser.parseFrame("""{"type":"ready","seq":1,"protocol_version":2}"""),
            BridgeStreamParser.parseFrame("""{"type":"event","seq":2,"role":"assistant","text":"working"}"""),
            BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":3,"tool":"bash","correlation_id":"p1"}"""),
            BridgeStreamParser.parseFrame("""{"type":"workflow_gate","seq":4,"gate_id":"g1"}"""),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(FakeBridgeConnector(FakeBridgeTransport(frames = frames)), scope)

        controller.connect()
        controller.sendPrompt("do the thing")
        controller.sendCommand("workflow_gate_response")

        val state = controller.state.value
        assertEquals(ConnectionState.CONNECTED_STREAMING, state.connection)
        assertEquals(4, state.frames.size)
        assertTrue(state.frames.any { it.type == BridgeFrameType.PERMISSION_REQUEST })
        assertTrue(state.frames.any { it.type == BridgeFrameType.WORKFLOW_GATE })
        assertTrue(state.sentLog.any { it.contains("prompt") && it.contains("ok") })
        assertTrue(state.sentLog.any { it.contains("workflow_gate_response") && it.contains("ok") })
        scope.cancel()
    }

    @Test
    fun stressLongStreamReducesCleanly() {
        val n = 5000
        val body = buildString {
            for (seq in 1..n) append("{\"type\":\"event\",\"seq\":$seq}\n")
        }
        val parser = BridgeStreamParser()
        val reducer = TimelineReducer()
        var emitted = 0
        // feed in 64-frame chunks to exercise incremental buffering
        var offset = 0
        val lines = body.split("\n")
        val chunk = StringBuilder()
        for ((i, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            chunk.append(line).append("\n")
            if (i % 64 == 0) {
                for (f in parser.feed(chunk.toString())) { reducer.accept(f); emitted++ }
                chunk.clear()
            }
        }
        for (f in parser.feed(chunk.toString()) + parser.flush()) { reducer.accept(f); emitted++ }
        offset += emitted
        assertEquals(n, emitted)
        assertEquals(n.toLong(), reducer.state.lastSeq)
        assertEquals(n, reducer.state.applied)
        assertFalse(reducer.state.desynced)
    }

    @Test
    fun replayResumeDuplicateGapReset() {
        val reducer = TimelineReducer()
        // initial batch
        for (seq in 1..3) reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":$seq}""")!!)
        assertEquals(3L, reducer.state.lastSeq)
        // reconnect replay re-delivers seq<=lastSeq -> duplicates
        assertEquals(FrameDisposition.DUPLICATE, reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":2}""")!!))
        // gap
        assertEquals(FrameDisposition.GAP, reducer.accept(BridgeStreamParser.parseFrame("""{"type":"event","seq":9}""")!!))
        assertTrue(reducer.state.desynced)
        // reset (replay_window_exceeded) recovers
        assertEquals(
            FrameDisposition.RESET,
            reducer.accept(BridgeStreamParser.parseFrame("""{"type":"reset","seq":0,"reason":"replay_window_exceeded"}""")!!),
        )
        assertFalse(reducer.state.desynced)
    }
}
