package io.devnogari.gajaedeck.bridge

import kotlin.random.Random

/**
 * Generates Idempotency-Key values. The same user action retried MUST reuse its original key
 * (use [PendingWrite.key]); a new action gets a new key.
 */
class IdempotencyKeys(private val random: Random = Random.Default) {
    private var counter: Long = 0

    fun next(prefix: String = "w"): String {
        counter += 1
        val rand = random.nextLong().toULong().toString(16)
        return "$prefix-$counter-$rand"
    }
}

/** A write awaiting server acknowledgement; retries reuse [key]. */
data class PendingWrite(
    val key: String,
    val type: String,
    val attempt: Int = 1,
) {
    fun retried(): PendingWrite = copy(attempt = attempt + 1)
}
