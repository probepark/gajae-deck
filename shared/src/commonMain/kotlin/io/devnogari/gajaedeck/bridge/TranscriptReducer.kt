package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class TranscriptReducer(private val redactor: Redactor) {
    fun reduce(frame: BridgeFrame): TranscriptItem? {
        val key = "${frame.type.wire}:${frame.seq ?: -1}"
        return when (frame.type) {
            BridgeFrameType.EVENT -> MessageItem(
                key = key,
                role = frame.raw.getString("role") ?: "assistant",
                text = redactor.redact(frame.raw.getString("text") ?: ""),
            )

            BridgeFrameType.RESPONSE -> MessageItem(
                key = key,
                role = "assistant",
                text = redactor.redact(frame.raw.getString("text") ?: ""),
            )

            BridgeFrameType.ERROR -> NoticeItem(
                key = key,
                kind = "error",
                raw = redactor.redact(frame.raw.toString()),
            )

            BridgeFrameType.HOST_TOOL_CALL -> {
                val tool = frame.raw.getString("tool") ?: "tool"
                ToolCallItem(
                    key = key,
                    tool = tool,
                    summary = redactor.redact(frame.raw.getString("summary") ?: tool),
                )
            }

            BridgeFrameType.PERMISSION_REQUEST,
            BridgeFrameType.UI_REQUEST,
            BridgeFrameType.WORKFLOW_GATE,
            BridgeFrameType.ELICITATION,
            BridgeFrameType.HOST_URI_REQUEST,
            -> GateItem(
                key = key,
                frameKind = frame.type.wire,
                correlationId = frame.raw.getString("correlation_id") ?: frame.raw.getString("gate_id") ?: "",
                preview = redactor.redact(
                    frame.raw.getString("tool") ?: frame.raw.getString("message") ?: frame.type.wire,
                ),
            )

            BridgeFrameType.READY,
            BridgeFrameType.RESET,
            -> NoticeItem(
                key = key,
                kind = frame.type.wire,
                raw = redactor.redact(frame.raw.toString()),
            )

            BridgeFrameType.UNKNOWN -> NoticeItem(
                key = key,
                kind = "unknown",
                raw = redactor.redact(frame.raw.toString()),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.getString(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull
}
