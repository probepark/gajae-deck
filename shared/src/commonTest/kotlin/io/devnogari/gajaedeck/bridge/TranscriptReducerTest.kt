package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TranscriptReducerTest {
    private val reducer = TranscriptReducer(Redactor(setOf("SECRET")))

    @Test
    fun eventMapsToRedactedMessageItem() {
        val item = reducer.reduce(frame("event", 7, "\"role\":\"user\",\"text\":\"hello SECRET\""))

        val message = assertIs<MessageItem>(item)
        assertEquals("event:7", message.key)
        assertEquals("user", message.role)
        assertFalse("SECRET" in message.text)
    }

    @Test
    fun responseMapsToAssistantMessageItem() {
        val item = reducer.reduce(frame("response", 8, "\"role\":\"ignored\",\"text\":\"answer SECRET\""))

        val message = assertIs<MessageItem>(item)
        assertEquals("response:8", message.key)
        assertEquals("assistant", message.role)
        assertFalse("SECRET" in message.text)
    }

    @Test
    fun errorMapsToRedactedNoticeItem() {
        val item = reducer.reduce(frame("error", 9, "\"message\":\"bad SECRET\""))

        val notice = assertIs<NoticeItem>(item)
        assertEquals("error:9", notice.key)
        assertEquals("error", notice.kind)
        assertFalse("SECRET" in notice.raw)
    }

    @Test
    fun hostToolCallMapsToToolCallItem() {
        val item = reducer.reduce(frame("host_tool_call", 10, "\"tool\":\"bash\",\"summary\":\"run SECRET\""))

        val toolCall = assertIs<ToolCallItem>(item)
        assertEquals("host_tool_call:10", toolCall.key)
        assertEquals("bash", toolCall.tool)
        assertFalse("SECRET" in toolCall.summary)
    }

    @Test
    fun gateFrameTypesMapToGateItems() {
        listOf(
            "permission_request",
            "ui_request",
            "workflow_gate",
            "host_uri_request",
        ).forEachIndexed { index, wire ->
            val item = reducer.reduce(frame(wire, index.toLong(), "\"correlation_id\":\"corr-$index\",\"tool\":\"tool-$index SECRET\""))

            val gate = assertIs<GateItem>(item)
            assertEquals("$wire:$index", gate.key)
            assertEquals(wire, gate.frameKind)
            assertEquals("corr-$index", gate.correlationId)
            assertFalse("SECRET" in gate.preview)
        }
    }

    @Test
    fun readyAndResetMapToNoticeItems() {
        listOf("ready", "reset").forEachIndexed { index, wire ->
            val item = reducer.reduce(frame(wire, (20 + index).toLong(), "\"message\":\"$wire SECRET\""))

            val notice = assertIs<NoticeItem>(item)
            assertEquals("$wire:${20 + index}", notice.key)
            assertEquals(wire, notice.kind)
            assertFalse("SECRET" in notice.raw)
        }
    }

    @Test
    fun unknownMapsToUnknownNoticeItem() {
        val item = reducer.reduce(frame("surprise", 30, "\"message\":\"unknown SECRET\""))

        val notice = assertIs<NoticeItem>(item)
        assertEquals("unknown:30", notice.key)
        assertEquals("unknown", notice.kind)
        assertFalse("SECRET" in notice.raw)
    }

    private fun frame(type: String, seq: Long, fields: String): BridgeFrame =
        BridgeStreamParser.parseFrame("""{"type":"$type","seq":$seq,$fields}""")!!
}
