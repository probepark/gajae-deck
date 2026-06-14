package io.devnogari.gajaedeck.bridge

/** Builds bridge endpoint URLs from a base URL and session id (see protocol-v2.md). */
class BridgeEndpoints(baseUrl: String, val sessionId: String? = null) {
    val base: String = baseUrl.trimEnd('/')

    val healthz: String get() = "$base/healthz"
    val help: String get() = "$base/v1/help"
    val handshake: String get() = "$base/v1/handshake"

    private fun session(): String =
        "$base/v1/sessions/" + (sessionId ?: error("sessionId required for session-scoped endpoint"))

    fun events(lastSeq: Long): String = "${session()}/events?last_seq=$lastSeq"
    val commands: String get() = "${session()}/commands"
    val controlClaim: String get() = "${session()}/control:claim"
    val controlDisconnect: String get() = "${session()}/control:disconnect"
    fun uiResponse(correlationId: String): String = "${session()}/ui-responses/$correlationId"
    fun hostToolResult(correlationId: String): String = "${session()}/host-tool-results/$correlationId"
    fun hostUriResult(correlationId: String): String = "${session()}/host-uri-results/$correlationId"

    fun withSession(id: String): BridgeEndpoints = BridgeEndpoints(base, id)
}
