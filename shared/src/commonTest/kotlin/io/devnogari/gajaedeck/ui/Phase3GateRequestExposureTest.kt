package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeEndpointsDescriptor
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeHandshakeAccepted
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase3GateRequestExposureTest {
    @Test
    fun exposesFullyNegotiatedPermissionGateAndFiltersResolvedGate() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = acceptedHandshake(acceptedCapabilities = fullyNegotiatedCapabilities()),
            frames = frames(
                """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","command":"echo ok"}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(FakeBridgeConnector(transport), scope)

        controller.connect()
        testScheduler.advanceUntilIdle()

        val request = assertNotNull(controller.state.value.gateRequests.singleOrNull())
        assertEquals("p1", request.correlationId)
        assertTrue(request.actions.isNotEmpty())
        assertNull(request.failClosedReason)

        controller.respondToGate("p1", "allow")
        testScheduler.advanceUntilIdle()

        assertTrue(controller.state.value.gateRequests.isEmpty())
        scope.cancel()
    }

    @Test
    fun exposesFailClosedReasonWhenPermissionCapabilityIsNotNegotiated() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = acceptedHandshake(
                acceptedCapabilities = fullyNegotiatedCapabilities().filterNot { it == "permission" },
            ),
            frames = frames(
                """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","command":"echo ok"}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(FakeBridgeConnector(transport), scope)

        controller.connect()
        testScheduler.advanceUntilIdle()

        val request = assertNotNull(controller.state.value.gateRequests.singleOrNull())
        assertEquals("p1", request.correlationId)
        assertNotNull(request.failClosedReason)
        scope.cancel()
    }

    private fun acceptedHandshake(acceptedCapabilities: List<String>): BridgeHandshakeResult =
        BridgeHandshakeResult.Accepted(
            BridgeHandshakeAccepted(
                status = "accepted",
                protocolVersion = BRIDGE_PROTOCOL_VERSION,
                sessionId = "s",
                acceptedCapabilities = acceptedCapabilities,
                acceptedScopes = listOf("control", "host_uri", "prompt"),
                endpoints = BridgeEndpointsDescriptor(uiResponses = "/u", hostUriResults = "/h"),
            ),
        )

    private fun fullyNegotiatedCapabilities(): List<String> = listOf(
        "events",
        "prompt",
        "permission",
        "workflow_gate",
        "ui.declarative",
        "elicitation",
        "host_uri",
    )

    private fun frames(vararg json: String): List<BridgeFrame> = json.map { BridgeStreamParser.parseFrame(it)!! }
}
