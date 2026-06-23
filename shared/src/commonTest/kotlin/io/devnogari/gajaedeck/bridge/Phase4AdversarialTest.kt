package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase4AdversarialTest {
    @Test
    fun commandRegistryDoesNotDriftFromCatalogOrExposeUnknownCommands() {
        val registryTypes = CommandRegistry.all.map { it.type }
        val catalogTypes = CommandCatalog.types

        assertEquals(CommandCatalog.types.toSet(), registryTypes.toSet())
        assertEquals(38, registryTypes.size)
        assertEquals(registryTypes.size, registryTypes.toSet().size, "CommandRegistry contains duplicate command types")
        assertEquals(12, CommandRegistry.exposed.size)
        assertTrue(CommandRegistry.exposed.all { it.type in catalogTypes })
    }

    @Test
    fun buildParamsCoercionSurvivesMalformedAndEdgeValues() {
        val metadata = CommandMetadata(
            type = "adversarial",
            group = CommandGroup.CONTROL,
            exposed = false,
            fields = listOf(
                FieldDescriptor("number", "Number", FieldKind.NUMBER, required = true),
                FieldDescriptor("badNumber", "Bad number", FieldKind.NUMBER, required = true),
                FieldDescriptor("badJson", "Bad JSON", FieldKind.JSON, required = true),
                FieldDescriptor("truthy", "Truthy", FieldKind.BOOLEAN, required = true),
                FieldDescriptor("falsey", "Falsey", FieldKind.BOOLEAN, required = true),
                FieldDescriptor("optionalBlank", "Optional blank", FieldKind.TEXT),
                FieldDescriptor("requiredText", "Required text", FieldKind.TEXT, required = true),
            ),
        )

        val params = CommandRegistry.buildParams(
            metadata,
            mapOf(
                "number" to "42",
                "badNumber" to "x",
                "badJson" to "{bad",
                "truthy" to "true",
                "falsey" to "TRUE",
                "optionalBlank" to "",
                "requiredText" to "present",
            ),
        )

        assertEquals(42.0, assertNotNull(params["number"]).jsonPrimitive.doubleOrNull)
        assertEquals("x", assertNotNull(params["badNumber"]).jsonPrimitive.content)
        assertEquals(JsonPrimitive("{bad"), params["badJson"])
        assertTrue(assertNotNull(params["truthy"]).jsonPrimitive.boolean)
        assertFalse(assertNotNull(params["falsey"]).jsonPrimitive.boolean)
        assertNull(params["optionalBlank"])
        assertEquals("present", assertNotNull(params["requiredText"]).jsonPrimitive.contentOrNull)
    }

    @Test
    fun commandEnablementDelegatesToScopeCatalog() {
        assertFalse(CommandRegistry.isEnabled("bash", emptySet()))
        assertTrue(CommandRegistry.isEnabled("bash", setOf(BridgeScope.BASH)))
        assertTrue(CommandRegistry.isEnabled("get_messages", setOf(BridgeScope.MESSAGE_READ)))
        assertFalse(CommandRegistry.isEnabled("handoff", setOf(BridgeScope.MESSAGE_READ)))
    }

    @Test
    fun commandGroupsMapFromBridgeScopes() {
        assertEquals(CommandGroup.EXECUTION, CommandRegistry.byType("bash")!!.group)
        assertEquals(CommandGroup.MODEL, CommandRegistry.byType("set_model")!!.group)
        assertEquals(CommandGroup.EXPORT, CommandRegistry.byType("export_html")!!.group)
        assertEquals(CommandGroup.SESSION, CommandRegistry.byType("switch_session")!!.group)
        assertEquals(CommandGroup.MESSAGE_READ, CommandRegistry.byType("get_messages")!!.group)
        assertEquals(CommandGroup.HOST_URI, CommandRegistry.byType("set_host_uri_schemes")!!.group)
    }
}
