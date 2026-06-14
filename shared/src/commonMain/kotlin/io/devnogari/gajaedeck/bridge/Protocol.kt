package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.json.Json

/** Frozen gjc Bridge protocol contract — see docs/bridge/protocol-v2.md. */
const val BRIDGE_PROTOCOL_VERSION: Int = 2

/** Shared JSON configuration: tolerant of unknown/forward-compatible fields. */
val bridgeJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

/** Server → client frame types streamed over the events endpoint. */
enum class BridgeFrameType(val wire: String) {
    READY("ready"),
    EVENT("event"),
    RESPONSE("response"),
    UI_REQUEST("ui_request"),
    PERMISSION_REQUEST("permission_request"),
    HOST_TOOL_CALL("host_tool_call"),
    HOST_URI_REQUEST("host_uri_request"),
    RESET("reset"),
    WORKFLOW_GATE("workflow_gate"),
    ERROR("error"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): BridgeFrameType =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** Command scopes advertised by the bridge (GJC_BRIDGE_SCOPES). */
enum class BridgeScope(val wire: String) {
    PROMPT("prompt"),
    CONTROL("control"),
    BASH("bash"),
    EXPORT("export"),
    SESSION("session"),
    MODEL("model"),
    MESSAGE_READ("message:read"),
    HOST_TOOLS("host_tools"),
    HOST_URI("host_uri"),
    ADMIN("admin");

    companion object {
        fun fromWire(value: String): BridgeScope? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Error codes. Server-reported codes come back as `{ "error": "<code>" }`; client-side
 * transport conditions are synthesized by [BridgeTransport] implementations.
 */
enum class BridgeErrorCode {
    // Server-reported
    ENDPOINT_DISABLED,
    UNAUTHORIZED,
    SCOPE_DENIED,
    INVALID_REQUEST,
    INVALID_JSON,
    INVALID_COMMAND,
    IDEMPOTENCY_CONFLICT,
    NOT_CONTROLLER,
    COMMANDS_UNAVAILABLE,
    INCOMPATIBLE_VERSION,

    // Client-side / transport
    NETWORK_BLOCKED,
    AUTH_BLOCKED,
    PROTOCOL_BLOCKED,
    TLS_BLOCKED,
    CORS_BLOCKED,
    FIRST_USE_CERTIFICATE_DECISION_REQUIRED,
    STREAM_BUFFERED,
    TIMEOUT,
    SERVER_REJECTED,
    STALE_RESPONSE,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): BridgeErrorCode = when (value) {
            "endpoint_disabled" -> ENDPOINT_DISABLED
            "unauthorized" -> UNAUTHORIZED
            "scope_denied" -> SCOPE_DENIED
            "invalid_request" -> INVALID_REQUEST
            "invalid_json" -> INVALID_JSON
            "invalid_command" -> INVALID_COMMAND
            "idempotency_conflict" -> IDEMPOTENCY_CONFLICT
            "not_controller" -> NOT_CONTROLLER
            "commands_unavailable" -> COMMANDS_UNAVAILABLE
            "incompatible_version" -> INCOMPATIBLE_VERSION
            else -> UNKNOWN
        }
    }
}
