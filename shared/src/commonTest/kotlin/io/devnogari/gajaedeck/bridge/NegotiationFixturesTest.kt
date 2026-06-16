package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NegotiationFixturesTest {
    private val redactor = Redactor()

    @Test
    fun fullAcceptedNegotiatesAllGateKinds() {
        val negotiation = negotiation(FULL_ACCEPTED_JSON)

        gateFrames.forEach { (kind, frame) ->
            assertNull(
                GateAdapters.adapt(frame, redactor, negotiation)?.failClosedReason,
                "$kind should be actionable with the full accepted handshake",
            )
        }
    }

    @Test
    fun protocolV1FailsClosedForNewGateKindsOnly() {
        val negotiation = negotiation(PROTOCOL_V1_JSON)

        assertActionable("permission_request", negotiation)
        assertActionable("workflow_gate", negotiation)
        assertFailClosed("ui_request", negotiation)
        assertFailClosed("elicitation", negotiation)
        assertFailClosed("host_uri_request", negotiation)
    }

    @Test
    fun missingUiResponsesEndpointFailsClosedForUiResponseGatesOnly() {
        val negotiation = negotiation(UIRESPONSES_MISSING_JSON)

        assertFailClosed("permission_request", negotiation)
        assertFailClosed("workflow_gate", negotiation)
        assertFailClosed("ui_request", negotiation)
        assertFailClosed("elicitation", negotiation)
        assertActionable("host_uri_request", negotiation)
    }

    @Test
    fun missingHostUriResultsEndpointFailsClosedForHostUriGateOnly() {
        val negotiation = negotiation(HOSTURIRESULTS_MISSING_JSON)

        assertActionable("permission_request", negotiation)
        assertActionable("workflow_gate", negotiation)
        assertActionable("ui_request", negotiation)
        assertActionable("elicitation", negotiation)
        assertFailClosed("host_uri_request", negotiation)
    }

    @Test
    fun scopeDeniedFailsClosedForAllGateKinds() {
        val negotiation = negotiation(SCOPE_DENIED_JSON)

        gateFrames.keys.forEach { kind ->
            assertFailClosed(kind, negotiation)
        }
    }

    private fun negotiation(json: String): NegotiationState {
        val accepted = bridgeJson.decodeFromString(BridgeHandshakeAccepted.serializer(), json)
        return NegotiationState(
            acceptedCapabilities = accepted.acceptedCapabilities.toSet(),
            availableEndpointKeys = buildSet {
                if (!accepted.endpoints.uiResponses.isNullOrEmpty()) add("uiResponses")
                if (!accepted.endpoints.hostUriResults.isNullOrEmpty()) add("hostUriResults")
            },
            grantedScopes = accepted.acceptedScopes.toSet(),
        )
    }

    private fun assertActionable(kind: String, negotiation: NegotiationState) {
        assertNull(
            GateAdapters.adapt(gateFrames.getValue(kind), redactor, negotiation)?.failClosedReason,
            "$kind should be actionable",
        )
    }

    private fun assertFailClosed(kind: String, negotiation: NegotiationState) {
        assertNotNull(
            GateAdapters.adapt(gateFrames.getValue(kind), redactor, negotiation)?.failClosedReason,
            "$kind should fail closed",
        )
    }

    private companion object {
        val gateFrames = mapOf(
            "permission_request" to frame("""{"type":"permission_request","seq":1,"correlation_id":"perm-1","tool":"bash","input":{}}"""),
            "workflow_gate" to frame("""{"type":"workflow_gate","seq":2,"correlation_id":"workflow-1","title":"Continue?"}"""),
            "ui_request" to frame("""{"type":"ui_request","seq":3,"correlation_id":"ui-1","title":"Choose","actions":[]}"""),
            "elicitation" to frame("""{"type":"elicitation","seq":4,"correlation_id":"elicitation-1","message":"Need input","fields":[]}"""),
            "host_uri_request" to frame("""{"type":"host_uri_request","seq":5,"correlation_id":"host-uri-1","uri":"file:///tmp/example.txt"}"""),
        )

        fun frame(json: String): BridgeFrame = BridgeStreamParser.parseFrame(json)!!

        const val FULL_ACCEPTED_JSON = """{"status":"accepted","protocol_version":2,"session_id":"<SESSION_ID>","accepted_capabilities":["events","prompt","permission","workflow_gate","host_tools","host_uri","ui.declarative","elicitation"],"accepted_scopes":["prompt","control","bash","export","session","model","message:read","host_tools","host_uri","admin"],"unsupported":[],"endpoints":{"events":"/v1/sessions/<SESSION_ID>/events","commands":"/v1/sessions/<SESSION_ID>/commands","uiResponses":"/v1/sessions/<SESSION_ID>/ui-responses/{correlation_id}","claimControl":"/v1/sessions/<SESSION_ID>/control:claim","disconnectControl":"/v1/sessions/<SESSION_ID>/control:disconnect","hostToolResults":"/v1/sessions/<SESSION_ID>/host-tool-results/{correlation_id}","hostUriResults":"/v1/sessions/<SESSION_ID>/host-uri-results/{correlation_id}"}}"""
        const val PROTOCOL_V1_JSON = """{"status":"accepted","protocol_version":1,"session_id":"<SESSION_ID>","accepted_capabilities":["events","prompt","permission","workflow_gate"],"accepted_scopes":["prompt","message:read","control"],"unsupported":[],"endpoints":{"events":"/v1/sessions/<SESSION_ID>/events","commands":"/v1/sessions/<SESSION_ID>/commands","uiResponses":"/v1/sessions/<SESSION_ID>/ui-responses/{correlation_id}"}}"""
        const val UIRESPONSES_MISSING_JSON = """{"status":"accepted","protocol_version":2,"session_id":"<SESSION_ID>","accepted_capabilities":["events","prompt","permission","workflow_gate","ui.declarative","elicitation","host_tools","host_uri"],"accepted_scopes":["prompt","message:read","control","session","model","export","host_uri"],"unsupported":[],"endpoints":{"events":"/v1/sessions/<SESSION_ID>/events","commands":"/v1/sessions/<SESSION_ID>/commands","hostUriResults":"/v1/sessions/<SESSION_ID>/host-uri-results/{correlation_id}"}}"""
        const val HOSTURIRESULTS_MISSING_JSON = """{"status":"accepted","protocol_version":2,"session_id":"<SESSION_ID>","accepted_capabilities":["events","prompt","permission","workflow_gate","ui.declarative","elicitation","host_tools","host_uri"],"accepted_scopes":["prompt","message:read","control","session","model","export","host_uri"],"unsupported":[],"endpoints":{"events":"/v1/sessions/<SESSION_ID>/events","commands":"/v1/sessions/<SESSION_ID>/commands","uiResponses":"/v1/sessions/<SESSION_ID>/ui-responses/{correlation_id}"}}"""
        const val SCOPE_DENIED_JSON = """{"status":"accepted","protocol_version":2,"session_id":"<SESSION_ID>","accepted_capabilities":["events","prompt","permission","workflow_gate","ui.declarative","elicitation","host_tools","host_uri"],"accepted_scopes":["prompt","message:read"],"unsupported":[],"endpoints":{"events":"/v1/sessions/<SESSION_ID>/events","commands":"/v1/sessions/<SESSION_ID>/commands","uiResponses":"/v1/sessions/<SESSION_ID>/ui-responses/{correlation_id}","claimControl":"/v1/sessions/<SESSION_ID>/control:claim","disconnectControl":"/v1/sessions/<SESSION_ID>/control:disconnect","hostToolResults":"/v1/sessions/<SESSION_ID>/host-tool-results/{correlation_id}","hostUriResults":"/v1/sessions/<SESSION_ID>/host-uri-results/{correlation_id}"}}"""
    }
}
