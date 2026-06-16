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
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPaletteTest {
    @Test
    fun controllerSubmitsRegistryCommandsAndReportsScopeDenied() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeBridgeTransport()
        val controller = SessionController(FakeBridgeConnector(transport), scope = scope)

        controller.connect()
        advanceUntilIdle()

        controller.sendCommand("get_state", JsonObject(emptyMap()))
        advanceUntilIdle()
        assertTrue(transport.sentCommands.any { it.first == "get_state" })

        val setModel = CommandRegistry.byType("set_model")!!
        controller.sendCommand(
            "set_model",
            CommandRegistry.buildParams(setModel, mapOf("model" to "opus")),
        )
        advanceUntilIdle()
        assertTrue(transport.sentCommands.any { it.first == "set_model" })

        scope.cancel()
    }

    @Test
    fun controllerLogsScopeDeniedForUnavailableCommand() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val transport = FakeBridgeTransport(grantedScopes = setOf(BridgeScope.MESSAGE_READ))
        val controller = SessionController(FakeBridgeConnector(transport), scope = scope)

        controller.connect()
        advanceUntilIdle()

        val bash = CommandRegistry.byType("bash")!!
        controller.sendCommand(
            "bash",
            CommandRegistry.buildParams(bash, mapOf("command" to "ls")),
        )
        advanceUntilIdle()

        assertFalse(transport.sentCommands.any { it.first == "bash" })
        assertTrue(controller.state.value.sentLog.any { it.contains("bash") && it.contains("error") })

        scope.cancel()
    }
}
