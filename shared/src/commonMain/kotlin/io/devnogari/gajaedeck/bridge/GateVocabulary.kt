package io.devnogari.gajaedeck.bridge

data class GateVocabularyEntry(
    val frameKind: String,
    val capability: String,
    val endpointKey: String,
    val requiredScopes: Set<String>,
)

object GateVocabulary {
    val entries: List<GateVocabularyEntry> = listOf(
        GateVocabularyEntry("permission_request", "permission", "uiResponses", setOf("control")),
        GateVocabularyEntry("workflow_gate", "workflow_gate", "uiResponses", setOf("control")),
        GateVocabularyEntry("ui_request", "ui.declarative", "uiResponses", setOf("control")),
        GateVocabularyEntry("elicitation", "elicitation", "uiResponses", setOf("control")),
        GateVocabularyEntry("host_uri_request", "host_uri", "hostUriResults", setOf("control", "host_uri")),
    )

    private val byKind = entries.associateBy { it.frameKind }

    fun forFrameKind(frameKind: String): GateVocabularyEntry? = byKind[frameKind]

    /** Fail-closed: actionable only when the capability is accepted, the endpoint key is available, and ALL required scopes are granted. */
    fun isActionable(
        frameKind: String,
        acceptedCapabilities: Set<String>,
        availableEndpointKeys: Set<String>,
        grantedScopes: Set<String>,
    ): Boolean {
        val e = byKind[frameKind] ?: return false
        if (e.capability !in acceptedCapabilities) return false
        if (e.endpointKey !in availableEndpointKeys) return false
        if (!grantedScopes.containsAll(e.requiredScopes)) return false
        return true
    }
}
