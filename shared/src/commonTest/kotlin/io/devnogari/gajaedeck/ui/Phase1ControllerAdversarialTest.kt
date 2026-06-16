package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.bridge.GateItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Phase1ControllerAdversarialTest {
    @Test
    fun duplicateFramesAreSkippedButGateIsIndexed() = runTest {
        val frames = listOf(
            BridgeStreamParser.parseFrame("""{"type":"event","seq":1,"role":"assistant","text":"first"}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"event","seq":1,"role":"assistant","text":"duplicate"}""")!!,
            BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":2,"correlation_id":"c1","prompt":"approve?"}""")!!,
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            connector = FakeBridgeConnector(FakeBridgeTransport(frames = frames)),
            scope = scope,
            initialLastSeq = 0,
        )

        controller.connect()

        val state = controller.state.value
        assertEquals(2, state.transcript.size)
        assertEquals(listOf("event:1", "permission_request:2"), state.transcript.map { it.key })
        assertEquals("first", state.transcript.first().let { assertIs<io.devnogari.gajaedeck.bridge.MessageItem>(it).text })
        val gate = assertIs<GateItem>(state.transcript.last())
        assertEquals("c1", gate.correlationId)
        assertEquals(1, state.pendingGates.size)
        assertEquals("c1", state.pendingGates.single().correlationId)

        scope.cancel()
    }
}
