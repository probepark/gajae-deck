package io.devnogari.gajaedeck.bridge

data class NegotiationState(
    val acceptedCapabilities: Set<String>,
    val availableEndpointKeys: Set<String>,
    val grantedScopes: Set<String>,
)

data class GateRequest(
    val frameKind: String,
    val correlationId: String,
    val gateId: String?,
    val endpointKey: String,
    val summary: String,
    val details: String,
    val actions: List<GateAction>,
    val fields: List<FieldDescriptor>,
    val failClosedReason: String?,
)
