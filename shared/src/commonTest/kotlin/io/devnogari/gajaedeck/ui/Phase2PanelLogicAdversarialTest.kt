package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.FieldDescriptor
import io.devnogari.gajaedeck.bridge.FieldKind
import io.devnogari.gajaedeck.bridge.GateAction
import io.devnogari.gajaedeck.bridge.GateActionStyle
import io.devnogari.gajaedeck.bridge.GateRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase2PanelLogicAdversarialTest {
    @Test
    fun canSubmitRejectsRequiredNumberWithNonNumericValue() {
        val request = request(fields = listOf(field(name = "count", kind = FieldKind.NUMBER, required = true)))

        assertFalse(ActionRequestPanelLogic.canSubmit(request, mapOf("count" to "not-a-number")))
    }

    @Test
    fun canSubmitRejectsFailClosedRequestEvenWhenAllFieldsAreValid() {
        val request = request(
            fields = listOf(field(name = "count", kind = FieldKind.NUMBER, required = true)),
            failClosedReason = "missing scope",
        )

        assertFalse(ActionRequestPanelLogic.canSubmit(request, mapOf("count" to "42")))
    }

    @Test
    fun canSubmitAcceptsOptionalEmptyFields() {
        val request = request(
            fields = listOf(
                field(name = "note", kind = FieldKind.TEXT),
                field(name = "count", kind = FieldKind.NUMBER),
                field(name = "choice", kind = FieldKind.ENUM, options = listOf("allow", "deny")),
            ),
        )

        assertTrue(ActionRequestPanelLogic.canSubmit(request, emptyMap()))
    }

    @Test
    fun fieldValidRejectsEnumValueOutsideOptions() {
        val field = field(name = "choice", kind = FieldKind.ENUM, required = true, options = listOf("allow", "deny"))

        assertFalse(ActionRequestPanelLogic.fieldValid(field, "maybe"))
    }

    private fun request(
        fields: List<FieldDescriptor> = emptyList(),
        failClosedReason: String? = null,
    ): GateRequest = GateRequest(
        frameKind = "ui_request",
        correlationId = "panel-adversarial",
        gateId = null,
        endpointKey = "uiResponses",
        summary = "Panel adversarial",
        details = "Panel adversarial",
        actions = listOf(GateAction("submit", "Submit", GateActionStyle.PRIMARY)),
        fields = fields,
        failClosedReason = failClosedReason,
    )

    private fun field(
        name: String,
        kind: FieldKind,
        required: Boolean = false,
        options: List<String> = emptyList(),
    ): FieldDescriptor = FieldDescriptor(
        name = name,
        label = name,
        kind = kind,
        required = required,
        options = options,
    )
}
