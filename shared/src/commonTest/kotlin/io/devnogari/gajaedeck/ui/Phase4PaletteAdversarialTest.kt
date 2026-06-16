package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BridgeScope
import io.devnogari.gajaedeck.bridge.CommandRegistry
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase4PaletteAdversarialTest {
    @Test
    fun scopeDeniedSubmitIsBlockedEndToEnd() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeBridgeTransport(grantedScopes = setOf(BridgeScope.MESSAGE_READ))
        val controller = SessionController(FakeBridgeConnector(transport), scope = scope)

        controller.connect()
        advanceUntilIdle()

        controller.sendCommand(
            "bash",
            CommandRegistry.buildParams(CommandRegistry.byType("bash")!!, mapOf("command" to "ls")),
        )
        advanceUntilIdle()

        assertFalse(transport.sentCommands.any { it.first == "bash" })
        assertTrue(controller.state.value.sentLog.any { it.contains("bash") && it.contains("error") })

        scope.cancel()
    }
}
