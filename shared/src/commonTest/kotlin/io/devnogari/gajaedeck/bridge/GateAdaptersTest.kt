package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateAdaptersTest {
    private val redactor = Redactor(setOf("SECRET_TOKEN"))
    private val fullyNegotiated = NegotiationState(
        acceptedCapabilities = setOf("permission", "workflow_gate", "ui.declarative", "elicitation", "host_uri"),
        availableEndpointKeys = setOf("uiResponses", "hostUriResults"),
        grantedScopes = setOf("control", "host_uri"),
    )

    @Test
    fun adaptsAllGateKindsWithEndpointKeysAndActions() {
        assertGate(
            type = "permission_request",
            fields = """"correlation_id":"p1","tool":"shell","command":"echo ok"""",
            endpointKey = "uiResponses",
            actions = listOf("allow", "deny", "always"),
        )
        assertGate(
            type = "workflow_gate",
            fields = """"correlation_id":"w1","title":"Proceed?","options":[{"id":"continue","label":"Continue"},"cancel"]""",
            endpointKey = "uiResponses",
            actions = listOf("continue", "cancel"),
        )
        assertGate(
            type = "ui_request",
            fields = """"correlation_id":"u1","title":"Input","fields":[]""",
            endpointKey = "uiResponses",
            actions = listOf("submit", "cancel"),
        )
        assertGate(
            type = "elicitation",
            fields = """"correlation_id":"e1","title":"Input","fields":[]""",
            endpointKey = "uiResponses",
            actions = listOf("submit", "cancel"),
        )
        assertGate(
            type = "host_uri_request",
            fields = """"correlation_id":"h1","uri":"https://h/p?x=1"""",
            endpointKey = "hostUriResults",
            actions = listOf("approve", "deny"),
        )
    }

    @Test
    fun failClosedReasonReflectsNegotiationGaps() {
        val hostUri = adapt(
            "host_uri_request",
            """"correlation_id":"h1","uri":"https://h/p"""",
            fullyNegotiated.copy(grantedScopes = setOf("control")),
        )
        assertNotNull(hostUri.failClosedReason)

        val uiRequest = adapt(
            "ui_request",
            """"correlation_id":"u1","title":"Input","fields":[]""",
            fullyNegotiated.copy(acceptedCapabilities = fullyNegotiated.acceptedCapabilities - "ui.declarative"),
        )
        assertNotNull(uiRequest.failClosedReason)
    }

    @Test
    fun redactsPermissionSecretsAndStripsHostUriQuery() {
        val permission = adapt(
            "permission_request",
            """"correlation_id":"p1","tool":"SECRET_TOKEN","command":"curl -H 'Authorization: Bearer abc.def' SECRET_TOKEN"""",
        )
        assertNoLeak(permission.summary)
        assertNoLeak(permission.details)
        assertFalse("abc.def" in permission.details)

        val hostUri = adapt(
            "host_uri_request",
            """"correlation_id":"h1","uri":"https://h/p?token=SECRET_TOKEN&x=1"""",
        )
        assertEquals("https://h/p", hostUri.details)
        assertNoLeak(hostUri.summary)
        assertNoLeak(hostUri.details)
        assertFalse("?" in hostUri.details)
        assertFalse("token=" in hostUri.details)
        assertFalse("x=1" in hostUri.details)
    }

    @Test
    fun parsesUiRequestAndElicitationFields() {
        val fieldsJson = """"fields":[{"name":"note","label":"Note SECRET_TOKEN","type":"multiline","required":true,"placeholder":"Bearer abc.def"},{"name":"choice","label":"Choice","type":"enum","options":["SECRET_TOKEN","safe"]},{"name":"enabled","label":"Enabled","type":"boolean"},{"name":"count","label":"Count","type":"number"},{"name":"payload","label":"Payload","type":"json"}]"""

        val uiRequest = adapt("ui_request", """"correlation_id":"u1","title":"Input",$fieldsJson""")
        assertParsedFields(uiRequest)

        val elicitation = adapt("elicitation", """"correlation_id":"e1","title":"Input",$fieldsJson""")
        assertParsedFields(elicitation)
    }

    @Test
    fun adaptReturnsNullForEventFrame() {
        assertNull(GateAdapters.adapt(frame("event", """"role":"assistant","text":"hello""""), redactor, fullyNegotiated))
    }

    private fun assertGate(type: String, fields: String, endpointKey: String, actions: List<String>) {
        val request = adapt(type, fields)
        assertEquals(type, request.frameKind)
        assertEquals(endpointKey, request.endpointKey)
        assertEquals(actions, request.actions.map { it.id })
        assertNull(request.failClosedReason)
    }

    private fun assertParsedFields(request: GateRequest) {
        assertEquals(listOf("note", "choice", "enabled", "count", "payload"), request.fields.map { it.name })
        assertEquals(FieldKind.MULTILINE, request.fields[0].kind)
        assertTrue(request.fields[0].required)
        assertFalse("SECRET_TOKEN" in request.fields[0].label)
        assertFalse("abc.def" in (request.fields[0].placeholder ?: ""))
        assertEquals(FieldKind.ENUM, request.fields[1].kind)
        assertEquals(listOf("***", "safe"), request.fields[1].options)
        assertEquals(FieldKind.BOOLEAN, request.fields[2].kind)
        assertEquals(FieldKind.NUMBER, request.fields[3].kind)
        assertEquals(FieldKind.JSON, request.fields[4].kind)
    }

    private fun assertNoLeak(value: String) {
        assertFalse("SECRET_TOKEN" in value)
        assertFalse("Bearer" in value)
    }

    private fun adapt(type: String, fields: String, negotiation: NegotiationState = fullyNegotiated): GateRequest =
        GateAdapters.adapt(frame(type, fields), redactor, negotiation)!!

    private fun frame(type: String, fields: String): BridgeFrame =
        BridgeStreamParser.parseFrame("""{"type":"$type","seq":1,$fields}""")!!
}
