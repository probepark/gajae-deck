package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase1AdversarialTest {
    @Test
    fun gateSurvivesEvictionAndResolvesExactlyOnce() {
        val transcript = TranscriptState(capacity = 2)
        val pendingGateIndex = PendingGateIndex()
        val correlationId = "corr-survives-eviction"
        val gate = GateItem(
            key = "gate:1",
            frameKind = "permission_request",
            correlationId = correlationId,
            preview = "Allow operation?",
        )

        transcript.add(gate)
        pendingGateIndex.put(
            PendingGate(
                correlationId = correlationId,
                frameKind = gate.frameKind,
                redactedPreview = gate.preview,
                allowedActions = listOf("allow", "deny"),
                endpointKey = "uiResponses",
                gateId = "gate-1",
                outboxGroup = "gate:$correlationId",
                outboxKey = correlationId,
                createdSeq = 1L,
            ),
        )

        repeat(5) { index ->
            transcript.add(NoticeItem(key = "notice:${index + 1}", kind = "notice", raw = "notice ${index + 1}"))
        }

        assertFalse(transcript.snapshot().any { it.key == gate.key }, "gate must be evicted from bounded transcript snapshot")
        assertTrue(pendingGateIndex.resolve(correlationId), "evicted gate must still resolve through PendingGateIndex")
        assertFalse(pendingGateIndex.resolve(correlationId), "gate resolution must be exact-once")
    }

    @Test
    fun reducerRedactsSecretsAcrossSemanticFrameTypes() {
        val reducer = TranscriptReducer(Redactor(setOf("SECRET")))
        val items = listOf(
            reducer.reduce(frame("event", 1, "\"role\":\"user\",\"text\":\"contains SECRET\"")),
            reducer.reduce(frame("response", 2, "\"text\":\"assistant SECRET\"")),
            reducer.reduce(frame("error", 3, "\"message\":\"error SECRET\"")),
            reducer.reduce(frame("host_tool_call", 4, "\"tool\":\"shell\",\"args\":{\"token\":\"SECRET\"}")),
            reducer.reduce(frame("permission_request", 5, "\"correlation_id\":\"c1\",\"prompt\":\"approve SECRET\"")),
            reducer.reduce(frame("ui_request", 6, "\"correlation_id\":\"c2\",\"prompt\":\"ui SECRET\"")),
            reducer.reduce(frame("workflow_gate", 7, "\"correlation_id\":\"c3\",\"prompt\":\"workflow SECRET\"")),
            reducer.reduce(frame("host_uri_request", 8, "\"correlation_id\":\"c4\",\"uri\":\"file://SECRET\"")),
        ).filterNotNull()

        assertEquals(8, items.size)
        items.forEach { item ->
            item.assertNoSecret()
        }
    }

    @Test
    fun transcriptStateMaintainsOrderingUnderHeavyChurn() {
        val transcript = TranscriptState(capacity = 100)

        repeat(1000) { index ->
            val number = index + 1
            transcript.add(NoticeItem(key = "notice:$number", kind = "notice", raw = "notice $number"))
        }

        val snapshot = transcript.snapshot()
        assertEquals(100, transcript.size)
        assertEquals(100, snapshot.size)
        assertEquals("notice:901", snapshot.first().key)
        assertEquals("notice:1000", snapshot.last().key)
    }

    @Test
    fun reducerUnknownFrameMapsToUnknownNoticeItem() {
        val reducer = TranscriptReducer(Redactor(setOf("SECRET")))

        val item = reducer.reduce(frame("unexpected_type", 42, "\"message\":\"SECRET in unknown\""))

        val notice = assertIs<NoticeItem>(item)
        assertEquals("unknown", notice.kind)
        assertEquals("unknown:42", notice.key)
        assertFalse("SECRET" in notice.raw)
    }

    private fun frame(type: String, seq: Long, fields: String): BridgeFrame =
        BridgeStreamParser.parseFrame("""{"type":"$type","seq":$seq,$fields}""")!!

    private fun TranscriptItem.assertNoSecret() {
        when (this) {
            is MessageItem -> {
                assertFalse("SECRET" in key)
                assertFalse("SECRET" in role)
                assertFalse("SECRET" in text)
            }
            is ToolCallItem -> {
                assertFalse("SECRET" in key)
                assertFalse("SECRET" in tool)
                assertFalse("SECRET" in summary)
            }
            is GateItem -> {
                assertFalse("SECRET" in key)
                assertFalse("SECRET" in frameKind)
                assertFalse("SECRET" in correlationId)
                assertFalse("SECRET" in preview)
            }
            is NoticeItem -> {
                assertFalse("SECRET" in key)
                assertFalse("SECRET" in kind)
                assertFalse("SECRET" in raw)
            }
        }
    }
}
