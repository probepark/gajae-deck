package io.devnogari.gajaedeck.bridge

/** Connection lifecycle states (see protocol-v2.md reconnect/replay design). */
enum class ConnectionState {
    PAIRING,
    CHECKING_HEALTH,
    CONNECTED_STREAMING,
    BACKOFF_RECONNECTING,
    REPLAYING,
    DESYNCED,
    AUTH_BLOCKED,
    TLS_BLOCKED,
    CORS_BLOCKED,
    PROTOCOL_BLOCKED,
    ENDPOINT_DISABLED,
    DISCONNECTED_BY_USER,
}

sealed interface ConnectionEvent {
    data object HealthOk : ConnectionEvent
    data object HealthFail : ConnectionEvent
    data object StreamOpened : ConnectionEvent
    data object StreamClosedEof : ConnectionEvent
    data object NetworkError : ConnectionEvent
    data object ReplayComplete : ConnectionEvent
    data object ReplayWindowExceeded : ConnectionEvent
    data object SnapshotRecovered : ConnectionEvent
    data object UserDisconnect : ConnectionEvent
    data object EditPairing : ConnectionEvent
    data class Blocked(val code: BridgeErrorCode) : ConnectionEvent
}

/** Pure connection state machine. */
data class ConnectionMachine(val state: ConnectionState = ConnectionState.PAIRING) {

    fun on(event: ConnectionEvent): ConnectionMachine = when (event) {
        ConnectionEvent.UserDisconnect -> to(ConnectionState.DISCONNECTED_BY_USER)
        ConnectionEvent.EditPairing -> to(ConnectionState.CHECKING_HEALTH)
        ConnectionEvent.HealthOk -> to(ConnectionState.CONNECTED_STREAMING)
        ConnectionEvent.HealthFail -> to(ConnectionState.BACKOFF_RECONNECTING)
        ConnectionEvent.StreamOpened -> if (state == ConnectionState.BACKOFF_RECONNECTING) {
            to(ConnectionState.REPLAYING)
        } else {
            to(ConnectionState.CONNECTED_STREAMING)
        }
        ConnectionEvent.StreamClosedEof,
        ConnectionEvent.NetworkError -> to(ConnectionState.BACKOFF_RECONNECTING)
        ConnectionEvent.ReplayComplete -> to(ConnectionState.CONNECTED_STREAMING)
        ConnectionEvent.ReplayWindowExceeded -> to(ConnectionState.DESYNCED)
        ConnectionEvent.SnapshotRecovered -> to(ConnectionState.CONNECTED_STREAMING)
        is ConnectionEvent.Blocked -> to(blockState(event.code))
    }

    private fun blockState(code: BridgeErrorCode): ConnectionState = when (code) {
        BridgeErrorCode.AUTH_BLOCKED, BridgeErrorCode.UNAUTHORIZED -> ConnectionState.AUTH_BLOCKED
        BridgeErrorCode.TLS_BLOCKED, BridgeErrorCode.FIRST_USE_CERTIFICATE_DECISION_REQUIRED -> ConnectionState.TLS_BLOCKED
        BridgeErrorCode.CORS_BLOCKED -> ConnectionState.CORS_BLOCKED
        BridgeErrorCode.PROTOCOL_BLOCKED, BridgeErrorCode.INCOMPATIBLE_VERSION -> ConnectionState.PROTOCOL_BLOCKED
        BridgeErrorCode.ENDPOINT_DISABLED -> ConnectionState.ENDPOINT_DISABLED
        else -> ConnectionState.BACKOFF_RECONNECTING
    }

    private fun to(next: ConnectionState) = copy(state = next)

    val isBlocked: Boolean
        get() = state in setOf(
            ConnectionState.AUTH_BLOCKED,
            ConnectionState.TLS_BLOCKED,
            ConnectionState.CORS_BLOCKED,
            ConnectionState.PROTOCOL_BLOCKED,
            ConnectionState.ENDPOINT_DISABLED,
        )
}
