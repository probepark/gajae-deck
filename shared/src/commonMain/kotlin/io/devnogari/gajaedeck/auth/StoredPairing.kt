package io.devnogari.gajaedeck.auth

import kotlinx.serialization.Serializable

/** How the bearer token is persisted on web (no secure OS storage there). */
enum class WebStorageMode { SESSION_ONLY, PERSISTENT }

/**
 * A saved bridge pairing. The token/ownerToken are secrets and must be redacted from logs
 * (see [Redactor]); on web, lower-assurance storage is flagged via [webStorageMode].
 */
@Serializable
data class StoredPairing(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val host: String,
    val port: Int,
    val token: String,
    val ownerToken: String? = null,
    val pinnedCertFingerprint: String? = null,
    val webStorageMode: WebStorageMode = WebStorageMode.PERSISTENT,
    val webTrustedTls: Boolean = false,
    val lastProtocolVersion: Int? = null,
    val lastSessionId: String? = null,
    val lastAcceptedSeq: Long = 0,
    val importedAt: Long = 0,
    val lastSuccessfulAuthAt: Long? = null,
    val lastAuthFailureAt: Long? = null,
) {
    /** Secret values that must never appear in logs/diagnostics. */
    fun secrets(): Set<String> = buildSet {
        add(token)
        ownerToken?.let { add(it) }
    }
}
