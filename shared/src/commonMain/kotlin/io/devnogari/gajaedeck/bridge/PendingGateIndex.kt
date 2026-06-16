package io.devnogari.gajaedeck.bridge

enum class GateState {
    PENDING,
    RESOLVED,
    STALE,
    ERROR,
}

data class PendingGate(
    val correlationId: String,
    val frameKind: String,
    val redactedPreview: String,
    val allowedActions: List<String>,
    val endpointKey: String,
    val gateId: String?,
    val outboxGroup: String,
    val outboxKey: String,
    val createdSeq: Long,
    val state: GateState = GateState.PENDING,
    val tool: String? = null,
)

class PendingGateIndex {
    private val gates = LinkedHashMap<String, PendingGate>()

    fun put(gate: PendingGate) {
        gates[gate.correlationId] = gate
    }

    fun get(correlationId: String): PendingGate? = gates[correlationId]

    fun snapshot(): List<PendingGate> = gates.values.toList()

    fun pending(): List<PendingGate> = gates.values.filter { it.state == GateState.PENDING }

    fun resolve(correlationId: String): Boolean {
        val gate = gates[correlationId] ?: return false
        if (gate.state != GateState.PENDING) return false
        gates[correlationId] = gate.copy(state = GateState.RESOLVED)
        return true
    }

    fun markStale(correlationId: String) {
        gates[correlationId]?.let { gate ->
            gates[correlationId] = gate.copy(state = GateState.STALE)
        }
    }

    fun markError(correlationId: String) {
        gates[correlationId]?.let { gate ->
            gates[correlationId] = gate.copy(state = GateState.ERROR)
        }
    }
}
