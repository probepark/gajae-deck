package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import io.devnogari.gajaedeck.bridge.BridgeException
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeHandshakeRequest
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeTransport
import io.devnogari.gajaedeck.bridge.BridgeScope
import io.devnogari.gajaedeck.bridge.CommandOutbox
import io.devnogari.gajaedeck.bridge.ConnectionState
import io.devnogari.gajaedeck.bridge.IdempotencyKeys
import io.devnogari.gajaedeck.bridge.ProtocolVersionRange
import io.devnogari.gajaedeck.bridge.TimelineReducer
import io.devnogari.gajaedeck.bridge.TimelineState
import io.devnogari.gajaedeck.bridge.FrameDisposition
import io.devnogari.gajaedeck.bridge.NegotiationState
import io.devnogari.gajaedeck.bridge.GateRequest
import io.devnogari.gajaedeck.bridge.GateAdapters
import io.devnogari.gajaedeck.bridge.GateState
import io.devnogari.gajaedeck.bridge.GateItem
import io.devnogari.gajaedeck.bridge.GateVocabulary
import io.devnogari.gajaedeck.bridge.PendingGate
import io.devnogari.gajaedeck.bridge.PendingGateIndex
import io.devnogari.gajaedeck.bridge.TranscriptItem
import io.devnogari.gajaedeck.bridge.TranscriptReducer
import io.devnogari.gajaedeck.bridge.TranscriptState
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class SessionUiState(
    val connection: ConnectionState = ConnectionState.PAIRING,
    val sessionId: String? = null,
    val transcript: List<TranscriptItem> = emptyList(),
    val pendingGates: List<PendingGate> = emptyList(),
    val gateRequests: List<GateRequest> = emptyList(),
    val sentLog: List<String> = emptyList(),
    val grantedScopes: Set<BridgeScope> = emptySet(),
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
    initialLastSeq: Long = 0L,
) {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val outbox = CommandOutbox()
    private val timeline = TimelineReducer(TimelineState(lastSeq = initialLastSeq))
    private val transcript = TranscriptState()
    private val transcriptReducer = TranscriptReducer(redactor)
    private val pendingGates = PendingGateIndex()
    private var negotiation = NegotiationState(emptySet(), emptySet(), emptySet())
    private val gateRequestsByCorr = LinkedHashMap<String, GateRequest>()
    private val alwaysAllowedTools = mutableSetOf<String>()

    /** The single resume cursor this controller streams from (server seq already committed). */
    val resumeCursor: Long get() = timeline.state.lastSeq
    private val keys = IdempotencyKeys()
    private var session: BridgeTransport? = null
    private val submittingGateIds = mutableSetOf<String>()

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
                            capabilities = listOf("events", "prompt", "permission", "workflow_gate", "ui.declarative", "elicitation", "host_tools", "host_uri"),
                            requestedScopes = listOf("prompt", "message:read", "control", "session", "model", "export", "host_uri"),
                    ),
                )
                if (handshake is BridgeHandshakeResult.Rejected) {
                    fail("handshake rejected: ${handshake.value.reason ?: "unknown"}")
                    return@launch
                }
                val accepted = (handshake as BridgeHandshakeResult.Accepted).value
                val ep = accepted.endpoints
                negotiation = NegotiationState(
                    acceptedCapabilities = accepted.acceptedCapabilities.toSet(),
                    availableEndpointKeys = buildSet {
                        if (!ep.uiResponses.isNullOrEmpty()) add("uiResponses")
                        if (!ep.hostUriResults.isNullOrEmpty()) add("hostUriResults")
                        if (!ep.hostToolResults.isNullOrEmpty()) add("hostToolResults")
                        if (!ep.commands.isNullOrEmpty()) add("commands")
                    },
                    grantedScopes = accepted.acceptedScopes.toSet(),
                )
            val grantedBridgeScopes = negotiation.grantedScopes.mapNotNull { BridgeScope.fromWire(it) }.toSet()
                val sessionId = accepted.sessionId
                val transport = connector.sessionTransport(sessionId)
                session = transport
                _state.update {
                it.copy(
                    connection = ConnectionState.CONNECTED_STREAMING,
                    sessionId = sessionId,
                    grantedScopes = grantedBridgeScopes,
                )
            }
                transport.events(timeline.state.lastSeq).collect { frame ->
                    val disposition = timeline.accept(frame)
                    if (disposition == FrameDisposition.DUPLICATE) return@collect
                    val item = transcriptReducer.reduce(frame) ?: return@collect
                    transcript.add(item)
                    if (item is GateItem && item.correlationId.isNotEmpty()) {
                        pendingGates.put(pendingGateFrom(frame, item))
                            GateAdapters.adapt(frame, redactor, negotiation)?.let { request ->
                                gateRequestsByCorr[request.correlationId] = request
                            }
                        if (item.frameKind == "permission_request" && frame.raw?.get("tool")?.jsonPrimitive?.contentOrNull in alwaysAllowedTools) {
                            respondToGate(item.correlationId, "allow")
                        }
                    }
                        _state.update {
                            it.copy(
                                transcript = transcript.snapshot(),
                                pendingGates = pendingGates.snapshot(),
                                gateRequests = activeGateRequests(),
                            )
                        }
                }
            }.onFailure { e -> fail(e) }
        }
    }

    private fun pendingGateFrom(frame: BridgeFrame, item: GateItem): PendingGate {
        val vocab = GateVocabulary.forFrameKind(item.frameKind)
        return PendingGate(
            correlationId = item.correlationId,
            frameKind = item.frameKind,
            redactedPreview = item.preview,
            allowedActions = emptyList(), // populated by gate adapters in Phase 2/3
            endpointKey = vocab?.endpointKey ?: "",
            gateId = frame.raw?.get("gate_id")?.jsonPrimitive?.contentOrNull,
            outboxGroup = "gate:${item.correlationId}",
            outboxKey = item.correlationId,
            createdSeq = frame.seq ?: 0L,
            tool = frame.raw?.get("tool")?.jsonPrimitive?.contentOrNull,
        )
    }
    private fun activeGateRequests(): List<GateRequest> =
        gateRequestsByCorr.values.filter { pendingGates.get(it.correlationId)?.state == GateState.PENDING }

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        send("prompt", buildJsonObject { put("message", text) })
    }

    fun respondToGate(correlationId: String, actionId: String, fieldValues: Map<String, String> = emptyMap()) {
        val transport = session ?: run { fail("not connected"); return }
        val gate = pendingGates.get(correlationId)
        if (gate == null || gate.state != GateState.PENDING) {
            log("gate $correlationId -> stale")
            return
        }
        // Fail-closed: refuse to post a response for a gate that was not negotiated (capability/endpoint/scope).
        if (gateRequestsByCorr[correlationId]?.failClosedReason != null) {
            log("gate $correlationId -> not negotiated")
            return
        }
        if (!submittingGateIds.add(correlationId)) {
            log("gate $correlationId -> already submitting")
            return
        }
        scope.launch {
            val key = gate.outboxKey
            outbox.enqueue(key, "gate_response", group = gate.outboxGroup)
            val body = buildJsonObject {
                put("action", actionId)
                gate.gateId?.let { put("gate_id", it) }
                for ((k, v) in fieldValues) put(k, v)
            }
            runCatching {
                when (gate.endpointKey) {
                    "hostUriResults" -> transport.postHostUriResult(correlationId, body, key)
                    "uiResponses" -> transport.postUiResponse(correlationId, body, key)
                    else -> throw BridgeException(BridgeErrorCode.INVALID_REQUEST, "unknown endpoint")
                }
            }.onSuccess {
                outbox.markAccepted(key)
                pendingGates.resolve(correlationId)
                if (actionId == "always" && gate.frameKind == "permission_request") {
                    gate.tool?.let { tool ->
                        alwaysAllowedTools.add(tool)
                        pendingGates.pending()
                            .filter { it.correlationId != correlationId && it.frameKind == "permission_request" && it.tool == tool }
                            .forEach { respondToGate(it.correlationId, "allow") }
                    }
                }
                log("gate ${gate.frameKind} -> ok")
                submittingGateIds.remove(correlationId)
            }.onFailure { e ->
                outbox.markRejected(key)
                pendingGates.markError(correlationId)
                val reason = (e as? BridgeException)?.let { errorHandler.handle(it).message } ?: redactor.redact(e.message ?: "error")
                log("gate ${gate.frameKind} -> error: $reason")
                submittingGateIds.remove(correlationId)
            }
            _state.update { it.copy(pendingGates = pendingGates.snapshot(), gateRequests = activeGateRequests()) }
        }
    }

    fun sendCommand(type: String) = send(type, JsonObject(emptyMap()))

    fun sendCommand(type: String, params: JsonObject) = send(type, params)

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
