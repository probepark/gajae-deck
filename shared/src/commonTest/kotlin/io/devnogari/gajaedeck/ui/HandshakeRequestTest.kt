package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class HandshakeRequestTest {
    @Test
    fun connectRequestsCanonicalPhase3Vocabulary() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeBridgeTransport()
        val controller = SessionController(FakeBridgeConnector(transport), scope)

        try {
            controller.connect()
            testScheduler.advanceUntilIdle()

            val request = assertNotNull(transport.lastHandshakeRequest)
            listOf(
                "events",
                "prompt",
                "permission",
                "workflow_gate",
                "ui.declarative",
                "elicitation",
                "host_tools",
                "host_uri",
            ).forEach { assertContains(request.capabilities, it) }
            listOf(
                "session",
                "model",
                "export",
                "host_uri",
                "control",
                "message:read",
                "prompt",
            ).forEach { assertContains(request.requestedScopes, it) }
        } finally {
            scope.cancel()
        }
    }
}
