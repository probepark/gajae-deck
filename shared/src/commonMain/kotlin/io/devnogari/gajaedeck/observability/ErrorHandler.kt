package io.devnogari.gajaedeck.observability

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import io.devnogari.gajaedeck.bridge.BridgeException

/**
 * Maps a throwable to a user-safe [UiError]. [BridgeException] codes become fixed, secret-free copy;
 * any other throwable's message is redacted (never shown raw) before display.
 */
class ErrorHandler(private val redactor: Redactor = Redactor()) {

    fun handle(throwable: Throwable): UiError = when (throwable) {
        is BridgeException -> forCode(throwable.code)
        else -> UiError(redactor.redact(throwable.message ?: DEFAULT_MESSAGE), UiErrorCategory.UNKNOWN)
    }

    /** Map a known bridge error code to fixed, secret-free user copy. */
    fun forCode(code: BridgeErrorCode): UiError = when (code) {
        BridgeErrorCode.UNAUTHORIZED, BridgeErrorCode.AUTH_BLOCKED ->
            UiError("Authentication failed. Check your token.", UiErrorCategory.AUTH)
        BridgeErrorCode.SCOPE_DENIED, BridgeErrorCode.NOT_CONTROLLER ->
            UiError("You don't have permission to run this command.", UiErrorCategory.PERMISSION)
        BridgeErrorCode.ENDPOINT_DISABLED, BridgeErrorCode.COMMANDS_UNAVAILABLE ->
            UiError("This feature is disabled on the bridge.", UiErrorCategory.PERMISSION)
        BridgeErrorCode.NETWORK_BLOCKED, BridgeErrorCode.TIMEOUT, BridgeErrorCode.CORS_BLOCKED ->
            UiError("Could not connect to the bridge. Check your network.", UiErrorCategory.NETWORK)
        BridgeErrorCode.TLS_BLOCKED, BridgeErrorCode.FIRST_USE_CERTIFICATE_DECISION_REQUIRED ->
            UiError("Certificate trust must be confirmed.", UiErrorCategory.PROTOCOL)
        BridgeErrorCode.INCOMPATIBLE_VERSION, BridgeErrorCode.PROTOCOL_BLOCKED,
        BridgeErrorCode.INVALID_REQUEST, BridgeErrorCode.INVALID_JSON, BridgeErrorCode.INVALID_COMMAND ->
            UiError("A protocol error occurred.", UiErrorCategory.PROTOCOL)
        BridgeErrorCode.IDEMPOTENCY_CONFLICT, BridgeErrorCode.STREAM_BUFFERED,
        BridgeErrorCode.SERVER_REJECTED, BridgeErrorCode.STALE_RESPONSE, BridgeErrorCode.UNKNOWN ->
            UiError("A bridge error occurred (${code.name}).", UiErrorCategory.UNKNOWN)
    }

    private companion object {
        const val DEFAULT_MESSAGE = "An unknown error occurred."
    }
}
