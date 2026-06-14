package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BridgeScope
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.ConnectionState
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionControllerTest {

    private fun frames() = listOfNotNull(
        BridgeStreamParser.parseFrame("""{"type":"ready","seq":1}"""),
        BridgeStreamParser.parseFrame("""{"type":"event","seq":2}"""),
    )

    @Test
    fun connectStreamsFramesAndUpdatesState() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(FakeBridgeTransport(frames = frames()), scope)
        controller.connect()
        val state = controller.state.value
        assertEquals(ConnectionState.CONNECTED_STREAMING, state.connection)
        assertEquals(2, state.frames.size)
        scope.cancel()
    }

    @Test
    fun allowedCommandLogsOk() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(FakeBridgeTransport(frames = frames()), scope)
        controller.connect()
        controller.sendCommand("get_session_stats")
        assertTrue(controller.state.value.sentLog.any { it.contains("get_session_stats") && it.contains("ok") })
        scope.cancel()
    }

    @Test
    fun scopeDeniedCommandLogsError() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = SessionController(
            FakeBridgeTransport(frames = frames(), grantedScopes = setOf(BridgeScope.MESSAGE_READ)),
            scope,
        )
        controller.connect()
        controller.sendCommand("bash") // requires BASH scope, not granted
        assertTrue(controller.state.value.sentLog.any { it.contains("bash") && it.contains("error") })
        scope.cancel()
    }
}
