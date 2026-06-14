package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.BridgeException
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeHandshakeRequest
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeTransport
import io.devnogari.gajaedeck.bridge.CommandOutbox
import io.devnogari.gajaedeck.bridge.ConnectionState
import io.devnogari.gajaedeck.bridge.IdempotencyKeys
import io.devnogari.gajaedeck.bridge.ProtocolVersionRange
import io.devnogari.gajaedeck.bridge.TimelineReducer
import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.observability.AppLogger
import io.devnogari.gajaedeck.observability.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SessionUiState(
    val connection: ConnectionState = ConnectionState.PAIRING,
    val sessionId: String? = null,
    val frames: List<BridgeFrame> = emptyList(),
    val sentLog: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * Drives a bridge session for the UI: health → handshake → (session-scoped) stream events; send commands.
 * Backed by a [BridgeConnector] so the same controller works against the live bridge or a fake.
 */
class SessionController(
    private val connector: BridgeConnector,
    private val scope: CoroutineScope,
    private val redactor: Redactor = Redactor(),
    private val errorHandler: ErrorHandler = ErrorHandler(redactor),
    private val logger: AppLogger = AppLogger.redacting(redactor),
) {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val outbox = CommandOutbox()
    private val timeline = TimelineReducer()
    private val keys = IdempotencyKeys()
    private var session: BridgeTransport? = null

    fun connect() {
        scope.launch {
            runCatching {
                _state.update { it.copy(connection = ConnectionState.CHECKING_HEALTH, error = null) }
                if (!connector.health()) {
                    fail("health check failed")
                    return@launch
                }
                val handshake = connector.handshake(
                    BridgeHandshakeRequest(
                        protocolVersionRange = ProtocolVersionRange(1, BRIDGE_PROTOCOL_VERSION),
                        capabilities = listOf("events", "prompt", "permission", "workflow_gate"),
                        requestedScopes = listOf("prompt", "message:read", "control"),
                    ),
                )
                if (handshake is BridgeHandshakeResult.Rejected) {
                    fail("handshake rejected: ${handshake.value.reason ?: "unknown"}")
                    return@launch
                }
                val sessionId = (handshake as BridgeHandshakeResult.Accepted).value.sessionId
                val transport = connector.sessionTransport(sessionId)
                session = transport
                _state.update { it.copy(connection = ConnectionState.CONNECTED_STREAMING, sessionId = sessionId) }
                transport.events(timeline.state.lastSeq).collect { frame ->
                    timeline.accept(frame)
                    _state.update { it.copy(frames = it.frames + frame) }
                }
            }.onFailure { e -> fail(e) }
        }
    }

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        send("prompt", buildJsonObject { put("text", text) })
    }

    fun sendCommand(type: String) = send(type, JsonObject(emptyMap()))

    private fun send(type: String, params: JsonObject) {
        val transport = session ?: run { fail("not connected"); return }
        scope.launch {
            val key = keys.next(type)
            outbox.enqueue(key, type)
            runCatching { transport.postCommand(type, params, key) }
                .onSuccess {
                    outbox.markAccepted(key)
                    log("$type → ok")
                }
                .onFailure { e ->
                    outbox.markRejected(key)
                    val reason = (e as? BridgeException)?.let { errorHandler.handle(it).message }
                        ?: redactor.redact(e.message ?: "error")
                    log("$type → error: $reason")
                }
        }
    }

    private fun log(line: String) {
        val safe = redactor.redact(line)
        logger.info(TAG, safe)
        _state.update { it.copy(sentLog = (it.sentLog + safe).takeLast(50)) }
    }

    /** Constructed failure message (redacted before it reaches the UI/logs). */
    private fun fail(message: String) {
        val safe = redactor.redact(message)
        logger.warn(TAG, safe)
        _state.update { it.copy(error = safe) }
    }

    /** Caught failure: mapped to user-safe copy via [ErrorHandler]; the raw throwable is only logged (redacted). */
    private fun fail(throwable: Throwable) {
        val uiError = errorHandler.handle(throwable)
        logger.error(TAG, uiError.message, throwable)
        _state.update { it.copy(error = uiError.message) }
    }

    private companion object {
        const val TAG = "SessionController"
    }
}
