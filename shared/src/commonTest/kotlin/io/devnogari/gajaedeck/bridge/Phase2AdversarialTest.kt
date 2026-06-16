package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase2AdversarialTest {
    private val redactor = Redactor(setOf("SECRET"))
    private val fullNegotiation = NegotiationState(
        acceptedCapabilities = setOf("permission", "workflow_gate", "ui.declarative", "elicitation", "host_uri"),
        availableEndpointKeys = setOf("uiResponses", "hostUriResults"),
        grantedScopes = setOf("control", "host_uri"),
    )

    @Test
    fun redactionLeakHuntMasksSecretsBearerTokensAndHostUriQuery() {
        gateFramesWithSecrets().forEach { (kind, frame) ->
            val request = GateAdapters.adapt(frame, redactor, fullNegotiation)
            assertNotNull(request, "$kind should adapt")
            val visible = buildList {
                add(request.summary)
                add(request.details)
                request.fields.forEach { field ->
                    add(field.label)
                    addAll(field.options)
                    field.placeholder?.let(::add)
                }
            }
            visible.forEach { value -> assertNoLeak(kind, value) }
            if (kind == "host_uri_request") {
                assertFalse("?access_token=SECRET&owner_token=SECRET&q=1" in request.details, "host_uri_request details leaked raw query")
                assertFalse("?access_token=SECRET&owner_token=SECRET&q=1" in request.summary, "host_uri_request summary leaked raw query")
                assertFalse("?" in request.summary, "host_uri_request summary retained a URI query")
            }
        }
    }

    @Test
    fun failClosedMatrixRequiresCapabilityEndpointAndScopeForEveryGateKind() {
        gateFramesWithSecrets().forEach { (kind, frame) ->
            val vocabulary = GateVocabulary.forFrameKind(kind)
            assertNotNull(vocabulary, "$kind should have vocabulary")

            assertNull(GateAdapters.adapt(frame, redactor, fullNegotiation)?.failClosedReason, "$kind full negotiation should be open")

            val withoutCapability = fullNegotiation.copy(acceptedCapabilities = fullNegotiation.acceptedCapabilities - vocabulary.capability)
            assertNotNull(GateAdapters.adapt(frame, redactor, withoutCapability)?.failClosedReason, "$kind should fail closed without capability")

            val withoutEndpoint = fullNegotiation.copy(availableEndpointKeys = fullNegotiation.availableEndpointKeys - vocabulary.endpointKey)
            assertNotNull(GateAdapters.adapt(frame, redactor, withoutEndpoint)?.failClosedReason, "$kind should fail closed without endpoint key")

            val withoutScope = fullNegotiation.copy(grantedScopes = fullNegotiation.grantedScopes - vocabulary.requiredScopes)
            assertNotNull(GateAdapters.adapt(frame, redactor, withoutScope)?.failClosedReason, "$kind should fail closed without scope")
        }
    }

    @Test
    fun malformedInputSkipsBadFieldsParsesMixedWorkflowOptionsAndDefaultsUnknownFieldType() {
        val uiRequest = adapt(
            "ui_request",
            "\"correlation_id\":\"u-malformed\",\"title\":\"Fields\",\"fields\":[\"bad\",{\"label\":\"missing name\"},{\"name\":\"kept\",\"label\":\"Kept SECRET\",\"type\":\"mystery\",\"required\":true},{\"name\":\"count\",\"label\":\"Count\",\"type\":\"number\"}]",
        )
        assertEquals(listOf("kept", "count"), uiRequest.fields.map { it.name })
        assertEquals(FieldKind.TEXT, uiRequest.fields.first { it.name == "kept" }.kind)
        assertTrue(uiRequest.fields.first { it.name == "kept" }.required)
        assertFalse("SECRET" in uiRequest.fields.first { it.name == "kept" }.label)

        val workflowGate = adapt(
            "workflow_gate",
            "\"correlation_id\":\"w-malformed\",\"title\":\"Proceed\",\"options\":[\"continue\",{\"id\":\"cancel\",\"label\":\"Cancel SECRET\"}]",
        )
        assertEquals(listOf("continue", "cancel"), workflowGate.actions.map { it.id })
        assertEquals(listOf("continue", "Cancel ***"), workflowGate.actions.map { it.label })
    }

    @Test
    fun adaptReturnsNullForNonGateFrames() {
        listOf("event", "error", "ready").forEach { type ->
            assertNull(GateAdapters.adapt(frame(type, "\"correlation_id\":\"noop\""), redactor, fullNegotiation), "$type should not adapt")
        }
    }

    private fun gateFramesWithSecrets(): List<Pair<String, BridgeFrame>> = listOf(
        "permission_request" to frame("permission_request", "\"correlation_id\":\"p-secret\",\"tool\":\"shell SECRET\",\"command\":\"echo SECRET && curl -H 'Authorization: Bearer abc.def.ghi' https://h?token=SECRET\""),
        "workflow_gate" to frame("workflow_gate", "\"correlation_id\":\"w-secret\",\"title\":\"Workflow SECRET Bearer abc.def.ghi token=SECRET\",\"message\":\"Details SECRET Bearer abc.def.ghi token=SECRET\",\"options\":[\"allow SECRET token=SECRET\",{\"id\":\"deny\",\"label\":\"Deny Bearer abc.def.ghi\"}]"),
        "ui_request" to frame("ui_request", "\"correlation_id\":\"u-secret\",\"title\":\"UI SECRET Bearer abc.def.ghi token=SECRET\",\"message\":\"Details SECRET Bearer abc.def.ghi token=SECRET\",\"fields\":[{\"name\":\"note\",\"label\":\"Note SECRET token=SECRET\",\"type\":\"text\",\"placeholder\":\"Bearer abc.def.ghi\"},{\"name\":\"choice\",\"label\":\"Choice\",\"type\":\"enum\",\"options\":[\"SECRET\",\"Bearer abc.def.ghi\",\"token=SECRET\"]}]"),
        "elicitation" to frame("elicitation", "\"correlation_id\":\"e-secret\",\"title\":\"Elicit SECRET Bearer abc.def.ghi token=SECRET\",\"message\":\"Details SECRET Bearer abc.def.ghi token=SECRET\",\"fields\":[{\"name\":\"note\",\"label\":\"Note SECRET token=SECRET\",\"type\":\"multiline\",\"placeholder\":\"Bearer abc.def.ghi\"}]"),
        "host_uri_request" to frame("host_uri_request", "\"correlation_id\":\"h-secret\",\"uri\":\"https://h/p?access_token=SECRET&owner_token=SECRET&q=1\",\"message\":\"Open SECRET Bearer abc.def.ghi token=SECRET\""),
    )

    private fun adapt(type: String, fields: String): GateRequest = GateAdapters.adapt(frame(type, fields), redactor, fullNegotiation)!!

    private fun frame(type: String, fields: String): BridgeFrame {
        val json = """{"type":"$type","seq":1,$fields}"""
        return BridgeStreamParser.parseFrame(json) ?: error("Could not parse frame JSON: $json")
    }

    private fun assertNoLeak(kind: String, value: String) {
        assertFalse("SECRET" in value, "$kind leaked seeded secret in $value")
        assertFalse("abc.def.ghi" in value, "$kind leaked bearer token body in $value")
        assertFalse("token=SECRET" in value, "$kind leaked key=value token in $value")
        assertFalse("access_token=SECRET" in value, "$kind leaked access_token in $value")
        assertFalse("owner_token=SECRET" in value, "$kind leaked owner_token in $value")
    }
}
