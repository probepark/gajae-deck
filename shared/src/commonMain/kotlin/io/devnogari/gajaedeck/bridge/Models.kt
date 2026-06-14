package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProtocolVersionRange(val min: Int, val max: Int)

@Serializable
data class BridgeHandshakeRequest(
    @SerialName("protocol_version_range") val protocolVersionRange: ProtocolVersionRange,
    val capabilities: List<String>,
    @SerialName("requested_scopes") val requestedScopes: List<String>,
    @SerialName("last_seq") val lastSeq: Long? = null,
)

@Serializable
data class BridgeEndpointsDescriptor(
    val events: String? = null,
    val commands: String? = null,
    val uiResponses: String? = null,
    val claimControl: String? = null,
    val disconnectControl: String? = null,
    val hostToolResults: String? = null,
    val hostUriResults: String? = null,
)

@Serializable
data class BridgeHandshakeAccepted(
    val status: String,
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("session_id") val sessionId: String,
    @SerialName("accepted_capabilities") val acceptedCapabilities: List<String> = emptyList(),
    @SerialName("accepted_scopes") val acceptedScopes: List<String> = emptyList(),
    val unsupported: List<String> = emptyList(),
    val endpoints: BridgeEndpointsDescriptor = BridgeEndpointsDescriptor(),
    @SerialName("frame_types") val frameTypes: List<String> = emptyList(),
)

@Serializable
data class BridgeHandshakeRejected(
    val status: String,
    val reason: String? = null,
    val message: String? = null,
)

/** Result of a handshake attempt. */
sealed interface BridgeHandshakeResult {
    data class Accepted(val value: BridgeHandshakeAccepted) : BridgeHandshakeResult
    data class Rejected(val value: BridgeHandshakeRejected) : BridgeHandshakeResult
}

/** Response envelope for a command POST. */
@Serializable
data class CommandResponse(
    val type: String = "response",
    val command: String? = null,
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: JsonElement? = null,
)

/** Parsed event-stream frame: typed envelope plus the raw payload for forward compatibility. */
data class BridgeFrame(
    val type: BridgeFrameType,
    val seq: Long?,
    val frameId: String?,
    val protocolVersion: Int?,
    val sessionId: String?,
    val raw: JsonObject,
) {
    val isReplayReset: Boolean
        get() = type == BridgeFrameType.RESET &&
            (raw["reason"]?.toString()?.contains("replay_window_exceeded") == true)
}
