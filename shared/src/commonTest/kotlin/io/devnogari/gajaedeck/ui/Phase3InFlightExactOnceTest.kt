package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeEndpointsDescriptor
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeHandshakeAccepted
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.bridge.GateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Phase3InFlightExactOnceTest {
    @Test
    fun inFlightGateResponsePostsExactlyOnce() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val responseGate = CompletableDeferred<Unit>()
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames("""{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}"""),
            responseGate = responseGate,
        )
        val controller = SessionController(FakeBridgeConnector(transport), scope)

        try {
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "allow")
            controller.respondToGate("p1", "allow")

            responseGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(1, transport.postedUiResponses.size)
            val gate = assertNotNull(controller.state.value.pendingGates.singleOrNull { it.correlationId == "p1" })
            assertEquals(GateState.RESOLVED, gate.state)
        } finally {
            scope.cancel()
        }
    }

    private fun negotiated() = BridgeHandshakeResult.Accepted(
        BridgeHandshakeAccepted(
            status = "accepted",
            protocolVersion = BRIDGE_PROTOCOL_VERSION,
            sessionId = "s",
            acceptedCapabilities = listOf("events", "prompt", "permission", "workflow_gate", "ui.declarative", "elicitation", "host_uri"),
            acceptedScopes = listOf("control", "host_uri", "prompt"),
            endpoints = BridgeEndpointsDescriptor(uiResponses = "/u", hostUriResults = "/h"),
        ),
    )

    private fun frames(vararg json: String): List<BridgeFrame> = json.map { BridgeStreamParser.parseFrame(it)!! }
}
