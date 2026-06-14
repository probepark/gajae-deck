package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import io.devnogari.gajaedeck.bridge.BridgeException
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeHandshakeRequest
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeTransport
import io.devnogari.gajaedeck.bridge.CommandResponse
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionControllerRedactionTest {

    private val secret = "super-secret-token-9z"

    private class ThrowingConnector(private val ex: Throwable) : BridgeConnector {
        override suspend fun health(): Boolean = throw ex
        override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult = throw ex
        override fun sessionTransport(sessionId: String): BridgeTransport = throw ex
    }

    /** Connects cleanly, then leaks the secret in a command failure to prove sentLog redaction. */
    private class TokenLeakingTransport(private val secret: String) : BridgeTransport {
        override suspend fun health(): Boolean = true
        override suspend fun help(): JsonObject = JsonObject(emptyMap())
        override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult =
            FakeBridgeTransport.defaultHandshake()
        override fun events(lastSeq: Long): Flow<BridgeFrame> = emptyFlow()
        override suspend fun postCommand(type: String, params: JsonObject, idempotencyKey: String): CommandResponse =
            throw BridgeException(BridgeErrorCode.SERVER_REJECTED, "rejected token=$secret Bearer $secret")
        override suspend fun postUiResponse(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
            throw UnsupportedOperationException()
        override suspend fun postHostToolResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
            throw UnsupportedOperationException()
        override suspend fun postHostUriResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
            throw UnsupportedOperationException()
    }

    private class SelfConnector(private val transport: BridgeTransport) : BridgeConnector {
        override suspend fun health(): Boolean = transport.health()
        override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult = transport.handshake(request)
        override fun sessionTransport(sessionId: String): BridgeTransport = transport
    }

    @Test
    fun connectErrorDoesNotLeakSecretIntoState() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            ThrowingConnector(IllegalStateException("connect failed token=$secret Authorization: Bearer $secret")),
            scope,
            redactor = Redactor(setOf(secret)),
        )
        controller.connect()
        val error = controller.state.value.error
        assertNotNull(error)
        assertFalse(error.contains(secret), "secret leaked into UI error: $error")
        scope.cancel()
    }

    @Test
    fun bridgeUnauthorizedShowsSafeAuthMessage() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            ThrowingConnector(BridgeException(BridgeErrorCode.UNAUTHORIZED, "token $secret rejected")),
            scope,
            redactor = Redactor(setOf(secret)),
        )
        controller.connect()
        val error = controller.state.value.error
        assertNotNull(error)
        assertFalse(error.contains(secret))
        assertTrue(error.contains("인증"), "expected the safe auth message, got: $error")
        scope.cancel()
    }

    @Test
    fun commandErrorDoesNotLeakSecretIntoSentLog() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            SelfConnector(TokenLeakingTransport(secret)),
            scope,
            redactor = Redactor(setOf(secret)),
        )
        controller.connect()
        controller.sendCommand("get_session_stats")
        val log = controller.state.value.sentLog
        assertTrue(log.isNotEmpty(), "expected a command log entry")
        log.forEach { assertFalse(it.contains(secret), "secret leaked into sentLog: $it") }
        scope.cancel()
    }

    @Test
    fun defaultTwoArgControllerScrubsHeaderAndTokenFormsFromState() = runTest {
        // The live path: two-arg SessionController uses the default (unseeded) Redactor, which must
        // still scrub raw token/ownerToken/Authorization/X-GJC-Bridge-Owner-Token value forms.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            ThrowingConnector(
                IllegalStateException("X-GJC-Bridge-Owner-Token: own3rSecret token=rawTok ownerToken=oTok Authorization: rawAuth"),
            ),
            scope,
        )
        controller.connect()
        val error = controller.state.value.error
        assertNotNull(error)
        listOf("own3rSecret", "rawTok", "oTok", "rawAuth").forEach {
            assertFalse(error.contains(it), "default redactor leaked '$it' into UI error: $error")
        }
        scope.cancel()
    }
}
