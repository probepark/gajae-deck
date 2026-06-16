package io.devnogari.gajaedeck.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingGateIndexTest {
    @Test
    fun putAndGetRoundTripsAllFields() {
        val index = PendingGateIndex()
        val gate = pendingGate(
            correlationId = "corr-1",
            frameKind = "workflow_gate",
            redactedPreview = "Approve deployment?",
            allowedActions = listOf("approve", "deny"),
            endpointKey = "uiResponses",
            gateId = "gate-1",
            outboxGroup = "group-1",
            outboxKey = "outbox-1",
            createdSeq = 42L,
            state = GateState.ERROR,
        )

        index.put(gate)

        assertEquals(gate, index.get("corr-1"))
    }

    @Test
    fun resolveReturnsTrueExactlyOnceThenFalseAndStoresResolved() {
        val index = PendingGateIndex()
        index.put(pendingGate(correlationId = "corr-1"))

        assertTrue(index.resolve("corr-1"))
        assertFalse(index.resolve("corr-1"))
        assertEquals(GateState.RESOLVED, index.get("corr-1")?.state)
    }

    @Test
    fun resolveUnknownIdReturnsFalse() {
        val index = PendingGateIndex()

        assertFalse(index.resolve("missing"))
    }

    @Test
    fun markStaleThenResolveReturnsFalse() {
        val index = PendingGateIndex()
        index.put(pendingGate(correlationId = "corr-1"))

        index.markStale("corr-1")

        assertFalse(index.resolve("corr-1"))
        assertEquals(GateState.STALE, index.get("corr-1")?.state)
    }

    @Test
    fun pendingExcludesResolvedStaleErrorWhileSnapshotKeepsInsertionOrder() {
        val index = PendingGateIndex()
        val pending = pendingGate(correlationId = "pending", createdSeq = 1L)
        val resolved = pendingGate(correlationId = "resolved", createdSeq = 2L)
        val stale = pendingGate(correlationId = "stale", createdSeq = 3L)
        val error = pendingGate(correlationId = "error", createdSeq = 4L)
        index.put(pending)
        index.put(resolved)
        index.put(stale)
        index.put(error)

        index.resolve("resolved")
        index.markStale("stale")
        index.markError("error")

        assertEquals(listOf("pending"), index.pending().map { it.correlationId })
        assertEquals(listOf("pending", "resolved", "stale", "error"), index.snapshot().map { it.correlationId })
    }

    @Test
    fun twoGatesWithSameOutboxGroupBothRegisterAndResolvingOneLeavesOtherPending() {
        val index = PendingGateIndex()
        val first = pendingGate(correlationId = "corr-1", outboxGroup = "shared", outboxKey = "outbox-1")
        val second = pendingGate(correlationId = "corr-2", outboxGroup = "shared", outboxKey = "outbox-2")
        index.put(first)
        index.put(second)

        assertTrue(index.resolve("corr-1"))

        assertEquals(GateState.RESOLVED, index.get("corr-1")?.state)
        assertEquals(GateState.PENDING, index.get("corr-2")?.state)
        assertEquals(listOf("corr-2"), index.pending().map { it.correlationId })
    }

    private fun pendingGate(
        correlationId: String = "corr",
        frameKind: String = "workflow_gate",
        redactedPreview: String = "redacted preview",
        allowedActions: List<String> = listOf("allow", "deny"),
        endpointKey: String = "uiResponses",
        gateId: String? = "gate",
        outboxGroup: String = "group",
        outboxKey: String = "outbox",
        createdSeq: Long = 1L,
        state: GateState = GateState.PENDING,
    ): PendingGate = PendingGate(
        correlationId = correlationId,
        frameKind = frameKind,
        redactedPreview = redactedPreview,
        allowedActions = allowedActions,
        endpointKey = endpointKey,
        gateId = gateId,
        outboxGroup = outboxGroup,
        outboxKey = outboxKey,
        createdSeq = createdSeq,
        state = state,
    )
}
