package io.devnogari.gajaedeck.bridge

sealed interface TranscriptItem {
    val key: String
}

data class MessageItem(
    override val key: String,
    val role: String,
    val text: String,
) : TranscriptItem

data class ToolCallItem(
    override val key: String,
    val tool: String,
    val summary: String,
) : TranscriptItem

data class GateItem(
    override val key: String,
    val frameKind: String,
    val correlationId: String,
    val preview: String,
) : TranscriptItem

data class NoticeItem(
    override val key: String,
    val kind: String,
    val raw: String,
) : TranscriptItem
