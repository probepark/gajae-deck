package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResumeCursorAdversarialTest {
    @Test
    fun zeroInitialCursorStillRequestsEventsExactlyOnce() = runTest {
        val transport = FakeBridgeTransport(
            frames = listOf(
                BridgeStreamParser.parseFrame("""{"type":"event","seq":1,"role":"assistant","text":"first"}""")!!,
            ),
        )
        val controllerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            connector = FakeBridgeConnector(transport),
            scope = controllerScope,
            initialLastSeq = 0L,
        )

        controller.connect()

        assertEquals(listOf(0L), transport.requestedLastSeqs)
        assertEquals(1L, controller.resumeCursor)
        controllerScope.cancel()
    }

    @Test
    fun initialCursorDropsAllFramesAtOrBelowCursor() = runTest {
        val transport = FakeBridgeTransport(
            frames = listOf(
                BridgeStreamParser.parseFrame("""{"type":"event","seq":500,"role":"assistant","text":"same"}""")!!,
                BridgeStreamParser.parseFrame("""{"type":"event","seq":499,"role":"assistant","text":"older"}""")!!,
                BridgeStreamParser.parseFrame("""{"type":"event","seq":1,"role":"assistant","text":"oldest"}""")!!,
            ),
        )
        val controllerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            connector = FakeBridgeConnector(transport),
            scope = controllerScope,
            initialLastSeq = 500L,
        )

        controller.connect()

        assertEquals(listOf(500L), transport.requestedLastSeqs)
        assertEquals(500L, controller.resumeCursor)
        assertEquals(emptyList(), controller.state.value.transcript)
        controllerScope.cancel()
    }
}
