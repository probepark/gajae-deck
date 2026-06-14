package io.devnogari.gajaedeck.bridge

import io.ktor.client.HttpClient

/**
 * Establishes a bridge session: health + handshake on the base URL, then yields a session-scoped
 * [BridgeTransport] (bound to the handshake-provided session id) for events/commands. This models
 * the real flow where the session id is only known after the handshake.
 */
interface BridgeConnector {
    suspend fun health(): Boolean
    suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult
    fun sessionTransport(sessionId: String): BridgeTransport
}

/** Live connector over Ktor; reuses one [HttpClient] across the base and session transports. */
class KtorBridgeConnector(
    private val baseUrl: String,
    private val token: String,
    private val ownerToken: String? = null,
    private val client: HttpClient = createBridgeHttpClient(),
) : BridgeConnector {
    private val base = KtorBridgeTransport(baseUrl, token, ownerToken = ownerToken, client = client)

    override suspend fun health(): Boolean = base.health()
    override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult = base.handshake(request)
    override fun sessionTransport(sessionId: String): BridgeTransport =
        KtorBridgeTransport(baseUrl, token, sessionId = sessionId, ownerToken = ownerToken, client = client)
}

/** Test/offline connector wrapping a single [FakeBridgeTransport]. */
class FakeBridgeConnector(private val transport: FakeBridgeTransport) : BridgeConnector {
    override suspend fun health(): Boolean = transport.health()
    override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult = transport.handshake(request)
    override fun sessionTransport(sessionId: String): BridgeTransport = transport
}
