package io.devnogari.gajaedeck.auth

import io.devnogari.gajaedeck.bridge.BridgeErrorCode

/** Diagnosis surfaced to the user when a pairing is persistently AuthBlocked. */
enum class AuthBlockedDiagnosis {
    MISSING_TOKEN,
    REJECTED_OR_ROTATED_TOKEN,
    FORBIDDEN_ENDPOINT,
    ENDPOINT_DISABLED,
    CORS_MASKING,
    UNKNOWN,
}

/**
 * Secret-level token primitive over a [SecureStore]: replace a pairing's token (clearing cached
 * session/pin/owner state) and diagnose auth failures. This is metadata-neutral (it never touches
 * [io.devnogari.gajaedeck.settings.PairingMetadata]), so it is safe for
 * [io.devnogari.gajaedeck.pairing.PairingRepository] to compose. Pairing deletion is owned by the
 * repository, not here, because deleting a secret without its metadata would cause drift.
 */
class TokenLifecycle(private val store: SecureStore) {

    /** Replace the bearer token without deleting the pairing; clears owner token, pin, and cached state. */
    suspend fun replaceToken(id: String, newToken: String, newOwnerToken: String? = null): StoredPairing? {
        val current = store.load(id) ?: return null
        val updated = current.copy(
            token = newToken,
            ownerToken = newOwnerToken,
            pinnedCertFingerprint = null,
            lastSessionId = null,
            lastAcceptedSeq = 0,
            lastAuthFailureAt = null,
        )
        store.save(updated)
        return updated
    }

    fun diagnose(code: BridgeErrorCode, hasToken: Boolean): AuthBlockedDiagnosis = when {
        !hasToken -> AuthBlockedDiagnosis.MISSING_TOKEN
        code == BridgeErrorCode.UNAUTHORIZED -> AuthBlockedDiagnosis.REJECTED_OR_ROTATED_TOKEN
        code == BridgeErrorCode.SCOPE_DENIED -> AuthBlockedDiagnosis.FORBIDDEN_ENDPOINT
        code == BridgeErrorCode.ENDPOINT_DISABLED -> AuthBlockedDiagnosis.ENDPOINT_DISABLED
        code == BridgeErrorCode.CORS_BLOCKED -> AuthBlockedDiagnosis.CORS_MASKING
        else -> AuthBlockedDiagnosis.UNKNOWN
    }
}
