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
 * Token lifecycle and recovery operations over a [SecureStore]: replace/delete a pairing's
 * token (clearing cached session/pin/owner state) and diagnose auth failures.
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

    suspend fun deletePairing(id: String) = store.delete(id)

    fun diagnose(code: BridgeErrorCode, hasToken: Boolean): AuthBlockedDiagnosis = when {
        !hasToken -> AuthBlockedDiagnosis.MISSING_TOKEN
        code == BridgeErrorCode.UNAUTHORIZED -> AuthBlockedDiagnosis.REJECTED_OR_ROTATED_TOKEN
        code == BridgeErrorCode.SCOPE_DENIED -> AuthBlockedDiagnosis.FORBIDDEN_ENDPOINT
        code == BridgeErrorCode.ENDPOINT_DISABLED -> AuthBlockedDiagnosis.ENDPOINT_DISABLED
        code == BridgeErrorCode.CORS_BLOCKED -> AuthBlockedDiagnosis.CORS_MASKING
        else -> AuthBlockedDiagnosis.UNKNOWN
    }
}
