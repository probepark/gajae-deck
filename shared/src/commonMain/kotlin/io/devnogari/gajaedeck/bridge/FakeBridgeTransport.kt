package io.devnogari.gajaedeck.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * In-memory [BridgeTransport] for tests and offline UI development.
 * Scripts handshake/help results, a sequence of event frames, and per-command responses;
 * enforces scope and idempotency the way the real bridge does.
 */
class FakeBridgeTransport(
    private val helpEndpointsEnabled: Boolean = true,
    private val grantedScopes: Set<BridgeScope> = BridgeScope.entries.toSet(),
    private val handshakeResult: BridgeHandshakeResult = defaultHandshake(),
    private val frames: List<BridgeFrame> = emptyList(),
    private val healthy: Boolean = true,
) : BridgeTransport {

    private val idempotencyCache = mutableMapOf<String, CommandResponse>()
    val sentCommands = mutableListOf<Pair<String, String>>() // type to idempotencyKey

    override suspend fun health(): Boolean = healthy

    override suspend fun help(): JsonObject = buildJsonObject {
        put("status", if (helpEndpointsEnabled) "experimental_gated" else "fail_closed")
    }

    override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult = handshakeResult

    override fun events(lastSeq: Long): Flow<BridgeFrame> = flow {
        for (f in frames) {
            if ((f.seq ?: Long.MAX_VALUE) > lastSeq || f.type == BridgeFrameType.RESET) emit(f)
        }
    }

    override suspend fun postCommand(type: String, params: JsonObject, idempotencyKey: String): CommandResponse {
        idempotencyCache[idempotencyKey]?.let { return it }
        if (!CommandCatalog.isKnown(type)) throw BridgeException(BridgeErrorCode.INVALID_COMMAND, "unknown command: $type")
        if (!CommandCatalog.isAllowed(type, grantedScopes)) {
            throw BridgeException(
                BridgeErrorCode.SCOPE_DENIED,
                "scope denied",
                scope = CommandCatalog.scopeFor(type)?.wire,
            )
        }
        sentCommands.add(type to idempotencyKey)
        val response = CommandResponse(type = "response", command = type, success = true)
        idempotencyCache[idempotencyKey] = response
        return response
    }

    override suspend fun postUiResponse(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        ack("ui_response", idempotencyKey)

    override suspend fun postHostToolResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        ack("host_tool_result", idempotencyKey)

    override suspend fun postHostUriResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        ack("host_uri_result", idempotencyKey)

    private fun ack(kind: String, idempotencyKey: String): CommandResponse {
        idempotencyCache[idempotencyKey]?.let { return it }
        val response = CommandResponse(type = "response", command = kind, success = true)
        idempotencyCache[idempotencyKey] = response
        return response
    }

    companion object {
        fun defaultHandshake(): BridgeHandshakeResult = BridgeHandshakeResult.Accepted(
            BridgeHandshakeAccepted(
                status = "accepted",
                protocolVersion = BRIDGE_PROTOCOL_VERSION,
                sessionId = "fake-session",
                acceptedCapabilities = listOf("events", "prompt"),
                acceptedScopes = BridgeScope.entries.map { it.wire },
            ),
        )
    }
}
