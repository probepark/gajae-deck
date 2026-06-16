package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.FieldDescriptor
import io.devnogari.gajaedeck.bridge.FieldKind
import io.devnogari.gajaedeck.bridge.GateRequest

object ActionRequestPanelLogic {
    /** Actions are enabled only when the gate is negotiated (no fail-closed reason). */
    fun actionsEnabled(request: GateRequest): Boolean = request.failClosedReason == null

    /** Validate a single field's current string value. */
    fun fieldValid(field: FieldDescriptor, value: String): Boolean {
        val trimmed = value.trim()
        if (field.required && trimmed.isEmpty()) return false
        return when (field.kind) {
            FieldKind.NUMBER -> trimmed.isEmpty() || trimmed.toDoubleOrNull() != null
            FieldKind.ENUM -> trimmed.isEmpty() || field.options.contains(trimmed)
            FieldKind.BOOLEAN -> trimmed.isEmpty() || trimmed == "true" || trimmed == "false"
            else -> true
        }
    }

    /** A submit-style action can fire only when actions are enabled and every field is valid. */
    fun canSubmit(request: GateRequest, values: Map<String, String>): Boolean =
        actionsEnabled(request) && request.fields.all { fieldValid(it, values[it.name] ?: "") }
}
