package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandRegistryTest {
    @Test
    fun registryIsCompleteAndUnique() {
        assertEquals(38, CommandRegistry.all.size)
        assertEquals(CommandCatalog.types.toSet(), CommandRegistry.all.map { it.type }.toSet())
        assertEquals(CommandRegistry.all.size, CommandRegistry.all.map { it.type }.toSet().size)
    }

    @Test
    fun exposedCommandsMatchPaletteContract() {
        val expected = setOf(
            "follow_up",
            "steer",
            "abort",
            "abort_and_prompt",
            "get_state",
            "get_messages",
            "new_session",
            "switch_session",
            "compact",
            "set_model",
            "set_thinking_level",
            "export_html",
        )

        assertEquals(12, CommandRegistry.exposed.size)
        assertEquals(expected, CommandRegistry.exposed.map { it.type }.toSet())
    }

    @Test
    fun commandEnablementUsesScopeGating() {
        assertFalse(CommandRegistry.isEnabled("bash", setOf(BridgeScope.MESSAGE_READ)))
        assertTrue(CommandRegistry.isEnabled("bash", setOf(BridgeScope.BASH)))
    }

    @Test
    fun buildParamsCoercesKnownFieldKinds() {
        val setModel = CommandRegistry.byType("set_model") ?: error("set_model missing")
        assertEquals(JsonPrimitive("opus"), CommandRegistry.buildParams(setModel, mapOf("model" to "opus"))["model"])

        val thinking = CommandRegistry.byType("set_thinking_level") ?: error("set_thinking_level missing")
        assertEquals(JsonPrimitive("high"), CommandRegistry.buildParams(thinking, mapOf("level" to "high"))["level"])

        val bash = CommandRegistry.byType("bash") ?: error("bash missing")
        assertEquals(JsonPrimitive("ls"), CommandRegistry.buildParams(bash, mapOf("command" to "ls"))["command"])

        val exportHtml = CommandRegistry.byType("export_html") ?: error("export_html missing")
        assertNull(CommandRegistry.buildParams(exportHtml, mapOf("path" to ""))["path"])

        val numbered = CommandMetadata(
            type = "numbered",
            group = CommandGroup.CONTROL,
            exposed = false,
            fields = listOf(FieldDescriptor("count", "Count", FieldKind.NUMBER)),
        )
        assertFalse(CommandRegistry.buildParams(numbered, mapOf("count" to "42"))["count"].toString().startsWith("\""))
    }

    @Test
    fun groupsMatchCommandScopes() {
        assertEquals(CommandGroup.EXECUTION, CommandRegistry.byType("bash")?.group)
        assertEquals(CommandGroup.MODEL, CommandRegistry.byType("set_model")?.group)
        assertEquals(CommandGroup.MESSAGE_READ, CommandRegistry.byType("get_messages")?.group)
    }
}
