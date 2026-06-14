package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Incremental, content-type-adaptive parser for the events stream.
 * Tolerates both NDJSON (one JSON object per line) and SSE (`data:` prefixed lines,
 * frames separated by blank lines). Partial trailing lines are buffered across [feed] calls.
 */
class BridgeStreamParser {
    private val buffer = StringBuilder()
    private val sseData = StringBuilder()

    fun feed(chunk: String): List<BridgeFrame> {
        buffer.append(chunk)
        val frames = mutableListOf<BridgeFrame>()
        while (true) {
            val nl = buffer.indexOf("\n")
            if (nl < 0) break
            val line = buffer.substring(0, nl).removeSuffix("\r")
            buffer.deleteRange(0, nl + 1)
            handleLine(line, frames)
        }
        return frames
    }

    /** Flush any buffered SSE/NDJSON content at end-of-stream. */
    fun flush(): List<BridgeFrame> {
        val frames = mutableListOf<BridgeFrame>()
        if (buffer.isNotEmpty()) {
            val line = buffer.toString().removeSuffix("\r")
            buffer.clear()
            handleLine(line, frames)
        }
        emitSse(frames)
        return frames
    }

    private fun handleLine(line: String, out: MutableList<BridgeFrame>) {
        when {
            line.isBlank() -> emitSse(out) // SSE event boundary
            line.startsWith(":") -> Unit // SSE comment
            line.startsWith("data:") -> sseData.append(line.removePrefix("data:").trim())
            line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:") -> Unit
            else -> parseFrame(line)?.let(out::add) // NDJSON
        }
    }

    private fun emitSse(out: MutableList<BridgeFrame>) {
        if (sseData.isNotEmpty()) {
            parseFrame(sseData.toString())?.let(out::add)
            sseData.clear()
        }
    }

    companion object {
        fun parseFrame(text: String): BridgeFrame? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val obj = runCatching {
                bridgeJson.parseToJsonElement(trimmed) as? JsonObject
            }.getOrNull() ?: return null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            return BridgeFrame(
                type = BridgeFrameType.fromWire(type),
                seq = obj["seq"]?.jsonPrimitive?.longOrNull,
                frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull,
                protocolVersion = obj["protocol_version"]?.jsonPrimitive?.longOrNull?.toInt(),
                sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull,
                raw = obj,
            )
        }

        /** One-shot parse of a complete body (NDJSON or SSE). */
        fun parseAll(body: String): List<BridgeFrame> {
            val parser = BridgeStreamParser()
            return parser.feed(body) + parser.flush()
        }
    }
}
