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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase3ResponseAdversarialTest {
    @Test
    fun failClosedActuatorDefaultHandshakeDoesNotPostOrResolve() = runTest {
        val transport = FakeBridgeTransport(
            frames = frames(
                """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "allow")
            testScheduler.advanceUntilIdle()

            assertTrue(transport.postedUiResponses.isEmpty())
            val gate = assertNotNull(controller.state.value.pendingGates.singleOrNull { it.correlationId == "p1" })
            assertEquals(GateState.PENDING, gate.state)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun wrongEndpointNeverRoutesHostUriAndUiResponsesSeparately() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames(
                """{"type":"host_uri_request","seq":1,"correlation_id":"h1","uri":"file:///tmp/a.txt","prompt":"Open?"}""",
                """{"type":"ui_request","seq":2,"correlation_id":"u1","prompt":"Name?","fields":[{"name":"name","label":"Name","type":"text"}]}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("h1", "allow")
            controller.respondToGate("u1", "submit", mapOf("name" to "Jin"))
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("h1"), transport.postedHostUriResults.map { it.first })
            assertEquals(listOf("u1"), transport.postedUiResponses.map { it.first })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun doubleTapExactOnceResolvesOnlyOnce() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames(
                """{"type":"permission_request","seq":1,"correlation_id":"p1","tool":"bash","prompt":"Run bash?"}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("p1", "allow")
            controller.respondToGate("p1", "allow")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("p1"), transport.postedUiResponses.map { it.first })
            val gate = assertNotNull(controller.state.value.pendingGates.singleOrNull { it.correlationId == "p1" })
            assertEquals(GateState.RESOLVED, gate.state)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun bodyContentIncludesActionAndFieldValues() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames(
                """{"type":"ui_request","seq":1,"correlation_id":"u1","prompt":"Name?","fields":[{"name":"name","label":"Name","type":"text"}]}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("u1", "submit", mapOf("name" to "Jin"))
            testScheduler.advanceUntilIdle()

            val body = assertNotNull(transport.postedUiResponses.singleOrNull()).second
            assertEquals("submit", body["action"]?.jsonPrimitive?.content)
            assertEquals("Jin", body["name"]?.jsonPrimitive?.content)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun gateIdPassthroughIncludesWorkflowGateId() = runTest {
        val transport = FakeBridgeTransport(
            handshakeResult = negotiated(),
            frames = frames(
                """{"type":"workflow_gate","seq":1,"correlation_id":"w1","gate_id":"g9","prompt":"Continue?"}""",
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val controller = SessionController(FakeBridgeConnector(transport), scope)
            controller.connect()
            testScheduler.advanceUntilIdle()

            controller.respondToGate("w1", "allow")
            testScheduler.advanceUntilIdle()

            val body = assertNotNull(transport.postedUiResponses.singleOrNull()).second
            assertEquals("g9", body["gate_id"]?.jsonPrimitive?.content)
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
