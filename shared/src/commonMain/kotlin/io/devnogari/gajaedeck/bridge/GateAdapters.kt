package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GateAdapters {
    fun adapt(frame: BridgeFrame, redactor: Redactor, negotiation: NegotiationState): GateRequest? {
        if (frame.type !in gateTypes) return null

        val frameKind = frame.type.wire
        val raw = frame.raw
        val vocabulary = GateVocabulary.forFrameKind(frameKind)
        val summary: String
        val details: String
        val actions: List<GateAction>
        val fields: List<FieldDescriptor>

        when (frame.type) {
            BridgeFrameType.PERMISSION_REQUEST -> {
                summary = redactor.redact("Permission: " + (raw.string("tool") ?: "command"))
                details = redactor.redact(raw.string("command") ?: raw["args"]?.toString() ?: "")
                actions = listOf(
                    GateAction("allow", "Allow", GateActionStyle.PRIMARY),
                    GateAction("deny", "Deny", GateActionStyle.DESTRUCTIVE),
                    GateAction("always", "Always allow", GateActionStyle.NEUTRAL),
                )
                fields = emptyList()
            }

            BridgeFrameType.WORKFLOW_GATE -> {
                summary = redactor.redact(raw.string("title") ?: raw.string("message") ?: "Workflow gate")
                details = redactor.redact(raw.string("message") ?: "")
                actions = parseWorkflowActions(raw["options"], redactor).ifEmpty {
                    listOf(
                        GateAction("continue", "Continue", GateActionStyle.PRIMARY),
                        GateAction("cancel", "Cancel", GateActionStyle.NEUTRAL),
                    )
                }
                fields = emptyList()
            }

            BridgeFrameType.UI_REQUEST,
            BridgeFrameType.ELICITATION,
            -> {
                summary = redactor.redact(raw.string("title") ?: "Input requested")
                details = redactor.redact(raw.string("message") ?: "")
                actions = listOf(
                    GateAction("submit", "Submit", GateActionStyle.PRIMARY),
                    GateAction("cancel", "Cancel", GateActionStyle.NEUTRAL),
                )
                fields = parseFields(raw["fields"], redactor)
            }

            BridgeFrameType.HOST_URI_REQUEST -> {
                summary = redactor.redact("Open URI")
                details = redactor.redact(stripUriQuery(raw.string("uri") ?: ""))
                actions = listOf(
                    GateAction("approve", "Approve", GateActionStyle.PRIMARY),
                    GateAction("deny", "Deny", GateActionStyle.DESTRUCTIVE),
                )
                fields = emptyList()
            }

            else -> return null
        }

        return GateRequest(
            frameKind = frameKind,
            correlationId = raw.string("correlation_id") ?: raw.string("gate_id") ?: "",
            gateId = raw.string("gate_id"),
            endpointKey = vocabulary?.endpointKey ?: "",
            summary = summary,
            details = details,
            actions = actions,
            fields = fields,
            failClosedReason = if (GateVocabulary.isActionable(
                    frameKind,
                    negotiation.acceptedCapabilities,
                    negotiation.availableEndpointKeys,
                    negotiation.grantedScopes,
                )
            ) {
                null
            } else {
                "not negotiated (capability/endpoint/scope unavailable)"
            },
        )
    }

    private val gateTypes = setOf(
        BridgeFrameType.PERMISSION_REQUEST,
        BridgeFrameType.WORKFLOW_GATE,
        BridgeFrameType.UI_REQUEST,
        BridgeFrameType.ELICITATION,
        BridgeFrameType.HOST_URI_REQUEST,
    )

    private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

    private fun stripUriQuery(uri: String): String = uri.substringBefore('?')

    private fun parseWorkflowActions(value: kotlinx.serialization.json.JsonElement?, redactor: Redactor): List<GateAction> =
        (value as? JsonArray).orEmpty().mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.let { option ->
                    val label = redactor.redact(option)
                    GateAction(option, label, GateActionStyle.NEUTRAL)
                }

                is JsonObject -> {
                    val id = item.string("id") ?: return@mapNotNull null
                    val label = redactor.redact(item.string("label") ?: id)
                    GateAction(id, label, GateActionStyle.NEUTRAL)
                }

                else -> null
            }
        }

    private fun parseFields(value: kotlinx.serialization.json.JsonElement?, redactor: Redactor): List<FieldDescriptor> =
        (value as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            FieldDescriptor(
                name = name,
                label = redactor.redact(obj.string("label") ?: name),
                kind = obj.string("type").toFieldKind(),
                required = obj["required"]?.jsonPrimitive?.booleanOrNull ?: false,
                options = (obj["options"] as? JsonArray).orEmpty().mapNotNull { option ->
                    option.jsonPrimitive.contentOrNull?.let(redactor::redact)
                },
                placeholder = obj.string("placeholder")?.let(redactor::redact),
            )
        }

    private fun String?.toFieldKind(): FieldKind = when (this) {
        "multiline" -> FieldKind.MULTILINE
        "boolean" -> FieldKind.BOOLEAN
        "enum" -> FieldKind.ENUM
        "number" -> FieldKind.NUMBER
        "json" -> FieldKind.JSON
        else -> FieldKind.TEXT
    }
}
