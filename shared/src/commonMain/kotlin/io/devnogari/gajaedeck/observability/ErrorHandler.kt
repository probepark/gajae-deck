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
            UiError("인증에 실패했습니다. 토큰을 확인하세요.", UiErrorCategory.AUTH)
        BridgeErrorCode.SCOPE_DENIED, BridgeErrorCode.NOT_CONTROLLER ->
            UiError("이 명령을 실행할 권한이 없습니다.", UiErrorCategory.PERMISSION)
        BridgeErrorCode.ENDPOINT_DISABLED, BridgeErrorCode.COMMANDS_UNAVAILABLE ->
            UiError("이 기능은 브리지에서 비활성화되어 있습니다.", UiErrorCategory.PERMISSION)
        BridgeErrorCode.NETWORK_BLOCKED, BridgeErrorCode.TIMEOUT, BridgeErrorCode.CORS_BLOCKED ->
            UiError("브리지에 연결할 수 없습니다. 네트워크를 확인하세요.", UiErrorCategory.NETWORK)
        BridgeErrorCode.TLS_BLOCKED, BridgeErrorCode.FIRST_USE_CERTIFICATE_DECISION_REQUIRED ->
            UiError("인증서 신뢰를 확인해야 합니다.", UiErrorCategory.PROTOCOL)
        BridgeErrorCode.INCOMPATIBLE_VERSION, BridgeErrorCode.PROTOCOL_BLOCKED,
        BridgeErrorCode.INVALID_REQUEST, BridgeErrorCode.INVALID_JSON, BridgeErrorCode.INVALID_COMMAND ->
            UiError("프로토콜 오류가 발생했습니다.", UiErrorCategory.PROTOCOL)
        BridgeErrorCode.IDEMPOTENCY_CONFLICT, BridgeErrorCode.STREAM_BUFFERED,
        BridgeErrorCode.SERVER_REJECTED, BridgeErrorCode.STALE_RESPONSE, BridgeErrorCode.UNKNOWN ->
            UiError("브리지 오류가 발생했습니다 (${code.name}).", UiErrorCategory.UNKNOWN)
    }

    private companion object {
        const val DEFAULT_MESSAGE = "알 수 없는 오류가 발생했습니다."
    }
}
