package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeEndpointsDescriptor
import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import io.devnogari.gajaedeck.bridge.BridgeHandshakeAccepted
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeException
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.bridge.GateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GateResponseTest {
    @Test
    fun gateResponsesRoutePerKindResolveAndIgnoreStaleOrUnknown() = runTest {
        val transport = FakeBridgeTransport(handshakeResult = negotiated(), frames = frames(
            """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}""",
            """{"type":"workflow_gate","seq":2,"correlation_id":"g1","prompt":"Continue?"}""",
            """{"type":"ui_request","seq":3,"correlation_id":"u1","title":"Input","fields":[{"name":"note","type":"text"}]}""",
            """{"type":"elicitation","seq":4,"correlation_id":"e1","message":"Need detail"}""",
            """{"type":"host_uri_request","seq":5,"correlation_id":"h1","uri":"file:///tmp/a"}""",
        ))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "allow")
            controller.respondToGate("g1", "approve")
            controller.respondToGate("u1", "submit", mapOf("note" to "ok"))
            controller.respondToGate("e1", "submit")
            controller.respondToGate("h1", "open")
            testScheduler.advanceUntilIdle()

            val uiIds = transport.postedUiResponses.map { it.first }
            assertEquals(listOf("p1", "g1", "u1", "e1"), uiIds)
            assertTrue(transport.postedUiResponses.all { it.second["action"]?.jsonPrimitive?.contentOrNull != null })
            assertEquals(listOf("h1"), transport.postedHostUriResults.map { it.first })
            assertFalse("h1" in uiIds)

            val gates = controller.state.value.pendingGates.associateBy { it.correlationId }
            for (id in listOf("p1", "g1", "u1", "e1", "h1")) {
                assertEquals(GateState.RESOLVED, assertNotNull(gates[id]).state)
            }

            controller.respondToGate("p1", "deny")
            controller.respondToGate("unknown", "allow")
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("p1", "g1", "u1", "e1"), transport.postedUiResponses.map { it.first })
            assertEquals(GateState.RESOLVED, assertNotNull(controller.state.value.pendingGates.associateBy { it.correlationId }["p1"]).state)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun gateResponseFailureMarksGateError() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames("""{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}"""),
            responseFailure = BridgeException(BridgeErrorCode.SERVER_REJECTED, "nope"),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "deny")
            testScheduler.advanceUntilIdle()

            val gate = assertNotNull(controller.state.value.pendingGates.singleOrNull { it.correlationId == "p1" })
            assertEquals(GateState.ERROR, gate.state)
            assertTrue(transport.postedUiResponses.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun alwaysAllowAutoRespondsToLaterPermissionForSameTool() = runTest {
        val transport = FakeBridgeTransport(handshakeResult = negotiated(), frames = frames(
            """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}""",
            """{"type":"permission_request","seq":2,"correlation_id":"p2","tool":"bash","prompt":"Run bash again?"}""",
        ))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "always")
            testScheduler.advanceUntilIdle()

            val gates = controller.state.value.pendingGates.associateBy { it.correlationId }
            assertEquals(GateState.RESOLVED, assertNotNull(gates["p1"]).state)
            assertEquals(GateState.RESOLVED, assertNotNull(gates["p2"]).state)
            assertEquals(listOf("p1", "p2"), transport.postedUiResponses.map { it.first })
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
