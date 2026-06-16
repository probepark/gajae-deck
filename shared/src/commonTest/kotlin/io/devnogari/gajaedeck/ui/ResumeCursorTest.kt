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

class ResumeCursorTest {
    @Test
    fun streamsFromInitialCursorExactlyOnce() = runTest {
        val transport = FakeBridgeTransport(
            frames = listOf(
                BridgeStreamParser.parseFrame("""{"type":"event","seq":78,"role":"assistant","text":"after"}""")!!,
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            connector = FakeBridgeConnector(transport),
            scope = scope,
            initialLastSeq = 77L,
        )

        controller.connect()

        assertEquals(listOf(77L), transport.requestedLastSeqs)
        assertEquals(78L, controller.resumeCursor)
        scope.cancel()
    }

    @Test
    fun ignoresFramesAtOrBelowCursor() = runTest {
        val transport = FakeBridgeTransport(
            frames = listOf(
                BridgeStreamParser.parseFrame("""{"type":"event","seq":77,"role":"assistant","text":"duplicate"}""")!!,
                BridgeStreamParser.parseFrame("""{"type":"event","seq":78,"role":"assistant","text":"after"}""")!!,
            ),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            connector = FakeBridgeConnector(transport),
            scope = scope,
            initialLastSeq = 77L,
        )

        controller.connect()

        assertEquals(78L, controller.resumeCursor)
        scope.cancel()
    }
}
