package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.FieldDescriptor
import io.devnogari.gajaedeck.bridge.FieldKind
import io.devnogari.gajaedeck.bridge.GateAction
import io.devnogari.gajaedeck.bridge.GateActionStyle
import io.devnogari.gajaedeck.bridge.GateRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionRequestPanelLogicTest {
    @Test
    fun actionsEnabledReflectsFailClosedReason() {
        assertFalse(ActionRequestPanelLogic.actionsEnabled(request(failClosedReason = "missing capability")))
        assertTrue(ActionRequestPanelLogic.actionsEnabled(request(failClosedReason = null)))
    }

    @Test
    fun fieldValidChecksRequiredAndTypedValues() {
        assertFalse(ActionRequestPanelLogic.fieldValid(field(required = true), "   "))

        val number = field(kind = FieldKind.NUMBER)
        assertFalse(ActionRequestPanelLogic.fieldValid(number, "not-a-number"))
        assertTrue(ActionRequestPanelLogic.fieldValid(number, "42.5"))

        val enum = field(kind = FieldKind.ENUM, options = listOf("allow", "deny"))
        assertFalse(ActionRequestPanelLogic.fieldValid(enum, "other"))
        assertTrue(ActionRequestPanelLogic.fieldValid(enum, "allow"))

        assertTrue(ActionRequestPanelLogic.fieldValid(field(kind = FieldKind.TEXT), ""))
    }

    @Test
    fun canSubmitRequiresNegotiatedGateAndValidFields() {
        val required = field(name = "reason", required = true)
        val validValues = mapOf("reason" to "approved")

        assertFalse(
            ActionRequestPanelLogic.canSubmit(
                request(fields = listOf(required), failClosedReason = "scope denied"),
                validValues,
            ),
        )
        assertFalse(
            ActionRequestPanelLogic.canSubmit(
                request(fields = listOf(required)),
                mapOf("reason" to ""),
            ),
        )
        assertTrue(
            ActionRequestPanelLogic.canSubmit(
                request(fields = listOf(required)),
                validValues,
            ),
        )
    }

    private fun request(
        fields: List<FieldDescriptor> = emptyList(),
        failClosedReason: String? = null,
    ) = GateRequest(
        frameKind = "action_request",
        correlationId = "corr-1",
        gateId = "gate-1",
        endpointKey = "endpoint",
        summary = "Summary",
        details = "Details",
        actions = listOf(GateAction("submit", "Submit", GateActionStyle.PRIMARY)),
        fields = fields,
        failClosedReason = failClosedReason,
    )

    private fun field(
        name: String = "value",
        kind: FieldKind = FieldKind.TEXT,
        required: Boolean = false,
        options: List<String> = emptyList(),
    ) = FieldDescriptor(
        name = name,
        label = "Value",
        kind = kind,
        required = required,
        options = options,
        placeholder = null,
    )
}
