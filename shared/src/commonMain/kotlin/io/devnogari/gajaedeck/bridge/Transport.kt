package io.devnogari.gajaedeck.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** A typed bridge failure surfaced by [BridgeTransport] implementations. */
class BridgeException(
    val code: BridgeErrorCode,
    override val message: String? = null,
    val scope: String? = null,
    val endpoint: String? = null,
) : Exception(message)

/**
 * Platform-agnostic contract for talking to a gjc bridge. Implementations live behind
 * platform actuals (Ktor/OkHttp, Darwin, Web Fetch) so trust/streaming behavior is swappable.
 */
interface BridgeTransport {
    suspend fun health(): Boolean

    suspend fun help(): JsonObject

    suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult

    /** Cold flow of frames from the events endpoint, resuming at [lastSeq]. */
    fun events(lastSeq: Long): Flow<BridgeFrame>

    suspend fun postCommand(
        type: String,
        params: JsonObject = JsonObject(emptyMap()),
        idempotencyKey: String,
    ): CommandResponse

    suspend fun postUiResponse(
        correlationId: String,
        body: JsonObject,
        idempotencyKey: String,
    ): CommandResponse

    suspend fun postHostToolResult(
        correlationId: String,
        body: JsonObject,
        idempotencyKey: String,
    ): CommandResponse

    suspend fun postHostUriResult(
        correlationId: String,
        body: JsonObject,
        idempotencyKey: String,
    ): CommandResponse
}
