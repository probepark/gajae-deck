package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateVocabularyTest {
    private val fullCapabilities = setOf("permission", "workflow_gate", "ui.declarative", "elicitation", "host_uri")
    private val fullEndpointKeys = setOf("uiResponses", "hostUriResults")
    private val fullScopes = setOf("control", "host_uri")

    @Test
    fun allGateFrameKindsResolveToDocumentedEndpoints() {
        val entries = listOf(
            GateVocabulary.forFrameKind("permission_request"),
            GateVocabulary.forFrameKind("workflow_gate"),
            GateVocabulary.forFrameKind("ui_request"),
            GateVocabulary.forFrameKind("elicitation"),
            GateVocabulary.forFrameKind("host_uri_request"),
        )

        assertEquals(5, entries.filterNotNull().size)
        assertEquals("hostUriResults", GateVocabulary.forFrameKind("host_uri_request")?.endpointKey)
        assertEquals("uiResponses", GateVocabulary.forFrameKind("permission_request")?.endpointKey)
        assertEquals("uiResponses", GateVocabulary.forFrameKind("workflow_gate")?.endpointKey)
        assertEquals("uiResponses", GateVocabulary.forFrameKind("ui_request")?.endpointKey)
        assertEquals("uiResponses", GateVocabulary.forFrameKind("elicitation")?.endpointKey)
    }

    @Test
    fun declarativeUiAndElicitationUseCanonicalCapabilities() {
        assertEquals("ui.declarative", GateVocabulary.forFrameKind("ui_request")?.capability)
        assertEquals("elicitation", GateVocabulary.forFrameKind("elicitation")?.capability)
    }

    @Test
    fun hostToolCallIsNotAUserInputGate() {
        assertNull(GateVocabulary.forFrameKind("host_tool_call"))
        assertFalse(
            GateVocabulary.isActionable(
                "host_tool_call",
                fullCapabilities,
                fullEndpointKeys,
                fullScopes,
            ),
        )
    }

    @Test
    fun actionableFailsClosedUntilCapabilityEndpointAndScopeArePresent() {
        assertFalse(
            GateVocabulary.isActionable(
                "host_uri_request",
                fullCapabilities - "host_uri",
                fullEndpointKeys,
                fullScopes,
            ),
        )
        assertFalse(
            GateVocabulary.isActionable(
                "host_uri_request",
                fullCapabilities,
                fullEndpointKeys - "hostUriResults",
                fullScopes,
            ),
        )
        assertFalse(
            GateVocabulary.isActionable(
                "host_uri_request",
                fullCapabilities,
                fullEndpointKeys,
                fullScopes - "host_uri",
            ),
        )
        assertTrue(
            GateVocabulary.isActionable(
                "host_uri_request",
                fullCapabilities,
                fullEndpointKeys,
                fullScopes,
            ),
        )
    }
    @Test
    fun everyGateFailsClosedWhenControlScopeMissing() {
        val frameKinds = listOf(
            "permission_request",
            "workflow_gate",
            "ui_request",
            "elicitation",
            "host_uri_request",
        )

        frameKinds.forEach { frameKind ->
            assertFalse(
                GateVocabulary.isActionable(
                    frameKind,
                    fullCapabilities,
                    fullEndpointKeys,
                    emptySet(),
                ),
                "Expected $frameKind to fail closed without control scope",
            )
        }
    }

}
