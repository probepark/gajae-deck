package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Phase5NegotiationAdversarialTest {
    private val redactor = Redactor()

    @Test
    fun capabilitiesWithoutEndpointKeysFailClosedForAllGates() {
        val negotiation = negotiation(capabilities = FULL_CAPABILITIES, scopes = FULL_SCOPES, endpoints = "{}")

        ALL_GATES.keys.forEach { kind -> assertFailClosed(kind, negotiation) }
    }

    @Test
    fun fullCapabilitiesAndEndpointsWithoutControlScopeFailClosedForAllGates() {
        val negotiation = negotiation(capabilities = FULL_CAPABILITIES, scopes = listOf("prompt"), endpoints = FULL_ENDPOINTS)

        ALL_GATES.keys.forEach { kind -> assertFailClosed(kind, negotiation) }
    }

    @Test
    fun missingOnlyDeclarativeUiCapabilityFailsClosedForUiRequestOnly() {
        val negotiation = negotiation(
            capabilities = FULL_CAPABILITIES - "ui.declarative",
            scopes = FULL_SCOPES,
            endpoints = FULL_ENDPOINTS,
        )

        assertActionable("permission_request", negotiation)
        assertActionable("workflow_gate", negotiation)
        assertFailClosed("ui_request", negotiation)
        assertActionable("elicitation", negotiation)
        assertActionable("host_uri_request", negotiation)
    }

    @Test
    fun missingOnlyHostUriCapabilityFailsClosedForHostUriRequestOnly() {
        val negotiation = negotiation(
            capabilities = FULL_CAPABILITIES - "host_uri",
            scopes = FULL_SCOPES,
            endpoints = FULL_ENDPOINTS,
        )

        assertActionable("permission_request", negotiation)
        assertActionable("workflow_gate", negotiation)
        assertActionable("ui_request", negotiation)
        assertActionable("elicitation", negotiation)
        assertFailClosed("host_uri_request", negotiation)
    }

    private fun negotiation(capabilities: List<String>, scopes: List<String>, endpoints: String): NegotiationState {
        val accepted = bridgeJson.decodeFromString(
            BridgeHandshakeAccepted.serializer(),
            handshakeJson(capabilities = capabilities.reversed(), scopes = scopes.reversed(), endpoints = endpoints),
        )
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
            GateAdapters.adapt(ALL_GATES.getValue(kind), redactor, negotiation)?.failClosedReason,
            "$kind should be actionable",
        )
    }

    private fun assertFailClosed(kind: String, negotiation: NegotiationState) {
        assertNotNull(
            GateAdapters.adapt(ALL_GATES.getValue(kind), redactor, negotiation)?.failClosedReason,
            "$kind should fail closed",
        )
    }

    private fun handshakeJson(capabilities: List<String>, scopes: List<String>, endpoints: String): String =
        """
        {
          "status":"accepted",
          "protocol_version":2,
          "session_id":"phase5-adversarial",
          "accepted_capabilities":[${capabilities.joinToString(",") { "\"$it\"" }}],
          "accepted_scopes":[${scopes.joinToString(",") { "\"$it\"" }}],
          "unsupported":[],
          "endpoints":$endpoints
        }
        """.trimIndent()

    private companion object {
        val FULL_CAPABILITIES = listOf(
            "events",
            "prompt",
            "permission",
            "workflow_gate",
            "ui.declarative",
            "elicitation",
            "host_tools",
            "host_uri",
        )
        val FULL_SCOPES = listOf("prompt", "control", "host_uri")
        const val FULL_ENDPOINTS = """
            {
              "events":"/v1/sessions/phase5-adversarial/events",
              "commands":"/v1/sessions/phase5-adversarial/commands",
              "uiResponses":"/v1/sessions/phase5-adversarial/ui-responses/{correlation_id}",
              "hostUriResults":"/v1/sessions/phase5-adversarial/host-uri-results/{correlation_id}"
            }
        """
        val ALL_GATES = mapOf(
            "permission_request" to frame("""{"type":"permission_request","seq":1,"correlation_id":"perm-1","tool":"bash","input":{}}"""),
            "workflow_gate" to frame("""{"type":"workflow_gate","seq":2,"correlation_id":"flow-1","title":"Review","message":"Continue?","options":["approve","deny"]}"""),
            "ui_request" to frame("""{"type":"ui_request","seq":3,"correlation_id":"ui-1","prompt":"Provide value","fields":[{"name":"value","label":"Value"}]}"""),
            "elicitation" to frame("""{"type":"elicitation","seq":4,"correlation_id":"elicit-1","prompt":"Provide input","fields":[{"name":"input","label":"Input"}]}"""),
            "host_uri_request" to frame("""{"type":"host_uri_request","seq":5,"correlation_id":"uri-1","uri":"file:///tmp/example.txt"}"""),
        )

        fun frame(json: String): BridgeFrame = BridgeStreamParser.parseFrame(json)!!
    }
}
