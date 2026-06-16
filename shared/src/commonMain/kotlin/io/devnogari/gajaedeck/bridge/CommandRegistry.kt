package io.devnogari.gajaedeck.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CommandMetadata(
    val type: String,
    val group: CommandGroup,
    val exposed: Boolean,
    val fields: List<FieldDescriptor>,
)

object CommandRegistry {
    val all: List<CommandMetadata> = CommandCatalog.commands.map { command ->
        CommandMetadata(
            type = command.type,
            group = command.scope.toCommandGroup(),
            exposed = command.type in EXPOSED,
            fields = fieldsFor(command.type),
        )
    }

    val exposed: List<CommandMetadata> = all.filter { it.exposed }

    fun byType(type: String): CommandMetadata? = all.firstOrNull { it.type == type }

    fun isEnabled(type: String, grantedScopes: Set<BridgeScope>): Boolean =
        CommandCatalog.isAllowed(type, grantedScopes)

    fun buildParams(meta: CommandMetadata, values: Map<String, String>): JsonObject = buildJsonObject {
        for (field in meta.fields) {
            val value = values[field.name] ?: continue
            if (value.isBlank() && !field.required) continue
            when (field.kind) {
                FieldKind.NUMBER -> value.toLongOrNull()?.let { put(field.name, it) }
                    ?: value.toDoubleOrNull()?.let { put(field.name, it) }
                    ?: put(field.name, value)
                FieldKind.BOOLEAN -> put(field.name, value == "true")
                FieldKind.JSON -> put(
                    field.name,
                    runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) },
                )
                FieldKind.TEXT,
                FieldKind.MULTILINE,
                FieldKind.ENUM,
                -> put(field.name, value)
            }
        }
    }
}

private val EXPOSED = setOf(
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

private fun fieldsFor(type: String): List<FieldDescriptor> = when (type) {
    "set_model" -> listOf(FieldDescriptor("model", "Model", FieldKind.TEXT, required = true))
    "set_thinking_level" -> listOf(
        FieldDescriptor(
            "level",
            "Thinking level",
            FieldKind.ENUM,
            required = true,
            options = listOf("off", "low", "medium", "high"),
        ),
    )
    "bash" -> listOf(FieldDescriptor("command", "Command", FieldKind.MULTILINE, required = true))
    "export_html" -> listOf(FieldDescriptor("path", "Path", FieldKind.TEXT, required = false))
    "steer",
    "follow_up",
    "abort_and_prompt",
    -> listOf(FieldDescriptor("message", "Message", FieldKind.MULTILINE, required = true))
    "switch_session",
    "get_branch_messages",
    -> listOf(FieldDescriptor("session_id", "Session ID", FieldKind.TEXT, required = true))
    "branch" -> listOf(FieldDescriptor("name", "Name", FieldKind.TEXT, required = true))
    "set_session_name" -> listOf(FieldDescriptor("name", "Name", FieldKind.TEXT, required = true))
    else -> emptyList()
}
