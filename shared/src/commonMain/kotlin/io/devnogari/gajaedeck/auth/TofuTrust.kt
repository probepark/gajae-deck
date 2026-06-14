package io.devnogari.gajaedeck.auth

/** Trust-on-first-use decision for a presented TLS certificate fingerprint. */
enum class TofuDecision { TRUST_FIRST_USE, MATCH, MISMATCH }

/**
 * Host-scoped TOFU verifier (native platforms). The pin is bound to a pairing's host; a
 * fingerprint that matches a different host, or differs from the stored pin, is a MISMATCH
 * (→ TlsBlocked). Web has no TOFU and must use browser-trusted TLS.
 */
class TofuVerifier(private val store: SecureStore) {

    suspend fun evaluate(pairingId: String, host: String, presentedFingerprint: String): TofuDecision {
        val pairing = store.load(pairingId) ?: return TofuDecision.TRUST_FIRST_USE
        val pinned = pairing.pinnedCertFingerprint ?: return TofuDecision.TRUST_FIRST_USE
        return when {
            pairing.host != host -> TofuDecision.MISMATCH // wrong-host isolation
            pinned.equals(presentedFingerprint, ignoreCase = true) -> TofuDecision.MATCH
            else -> TofuDecision.MISMATCH
        }
    }

    /** Pin the fingerprint after an explicit first-use acceptance. */
    suspend fun acceptFirstUse(pairingId: String, fingerprint: String): StoredPairing? =
        store.load(pairingId)?.let { p ->
            p.copy(pinnedCertFingerprint = fingerprint).also { store.save(it) }
        }

    /** Explicit rotation: replace an existing pin after the user confirms a new certificate. */
    suspend fun rotatePin(pairingId: String, newFingerprint: String): StoredPairing? =
        acceptFirstUse(pairingId, newFingerprint)
}
