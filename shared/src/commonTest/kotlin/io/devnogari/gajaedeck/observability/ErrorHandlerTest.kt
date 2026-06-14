package io.devnogari.gajaedeck.observability

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import io.devnogari.gajaedeck.bridge.BridgeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlerTest {

    @Test
    fun mapsBridgeCodesToSafeCategoriesWithoutLeakingMessage() {
        val handler = ErrorHandler()
        val unauthorized = handler.handle(BridgeException(BridgeErrorCode.UNAUTHORIZED, "token abc123 rejected"))
        assertEquals(UiErrorCategory.AUTH, unauthorized.category)
        assertFalse(unauthorized.message.contains("abc123"), "code-mapped message must not echo the raw exception")
        assertEquals(UiErrorCategory.PERMISSION, handler.forCode(BridgeErrorCode.SCOPE_DENIED).category)
        assertEquals(UiErrorCategory.NETWORK, handler.forCode(BridgeErrorCode.TIMEOUT).category)
    }

    @Test
    fun redactsNonBridgeThrowableMessage() {
        val secret = "secret-token-42"
        val handler = ErrorHandler(Redactor(setOf(secret)))
        val ui = handler.handle(IllegalStateException("failed using $secret"))
        assertEquals(UiErrorCategory.UNKNOWN, ui.category)
        assertFalse(ui.message.contains(secret), "non-bridge error message must be redacted")
    }

    @Test
    fun everyBridgeErrorCodeMapsToNonBlankCopy() {
        val handler = ErrorHandler()
        BridgeErrorCode.entries.forEach { code ->
            assertTrue(handler.forCode(code).message.isNotBlank(), "missing copy for $code")
        }
    }
}
