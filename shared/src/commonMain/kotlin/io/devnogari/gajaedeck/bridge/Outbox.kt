package io.devnogari.gajaedeck.bridge

/** Lifecycle of a local write awaiting server commitment under multi-controller concurrency. */
enum class WriteStatus { PENDING, ACCEPTED, REJECTED, STALE }

data class OutboxEntry(
    val key: String,
    val type: String,
    /** Exclusive group (e.g. a workflow gate id or "switch_session") — only one entry can win. */
    val group: String? = null,
    val attempt: Int = 1,
    val status: WriteStatus = WriteStatus.PENDING,
)

/**
 * Tracks local writes until the server commits/rejects them. Enforces multi-controller invariants:
 * - a retried action reuses its Idempotency-Key (same entry, bumped attempt) — never a duplicate;
 * - within an exclusive [OutboxEntry.group] (e.g. a gate answer, switch_session) exactly one entry
 *   is ACCEPTED and the rest become STALE;
 * - a response for an already-STALE key is a stale response (does not flip to ACCEPTED).
 * Timeline ordering remains the [TimelineReducer]'s job (server seq); this tracks command status by key.
 */
class CommandOutbox {
    private val entries = LinkedHashMap<String, OutboxEntry>()

    fun enqueue(key: String, type: String, group: String? = null): OutboxEntry {
        val existing = entries[key]
        val entry = existing?.copy(attempt = existing.attempt + 1, status = WriteStatus.PENDING)
            ?: OutboxEntry(key = key, type = type, group = group)
        entries[key] = entry
        return entry
    }

    fun markAccepted(key: String): OutboxEntry? = transition(key) {
        if (it.status == WriteStatus.STALE) it else it.copy(status = WriteStatus.ACCEPTED)
    }

    fun markRejected(key: String): OutboxEntry? = transition(key) {
        if (it.status == WriteStatus.STALE) it else it.copy(status = WriteStatus.REJECTED)
    }

    /** Server resolves an exclusive group: [winnerKey] is ACCEPTED, other pending peers go STALE. */
    fun resolveExclusive(group: String, winnerKey: String): List<OutboxEntry> {
        entries.values.filter { it.group == group }.forEach { e ->
            val next = when {
                e.key == winnerKey -> e.copy(status = WriteStatus.ACCEPTED)
                e.status == WriteStatus.PENDING -> e.copy(status = WriteStatus.STALE)
                else -> e
            }
            entries[e.key] = next
        }
        return entries.values.filter { it.group == group }
    }

    /** True if a server response for [key] arrived after it was already superseded (stale). */
    fun isStaleResponse(key: String): Boolean = entries[key]?.status == WriteStatus.STALE

    fun status(key: String): WriteStatus? = entries[key]?.status
    fun pending(): List<OutboxEntry> = entries.values.filter { it.status == WriteStatus.PENDING }
    fun all(): List<OutboxEntry> = entries.values.toList()

    private fun transition(key: String, f: (OutboxEntry) -> OutboxEntry): OutboxEntry? {
        val e = entries[key] ?: return null
        val next = f(e)
        entries[key] = next
        return next
    }
}
