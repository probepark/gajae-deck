package io.devnogari.gajaedeck.settings

import io.devnogari.gajaedeck.auth.WebStorageMode
import kotlinx.serialization.Serializable

/**
 * Non-secret pairing metadata persisted in [AppSettings]. Secrets (token/ownerToken) live only in
 * the platform [io.devnogari.gajaedeck.auth.SecureStore]; this type intentionally has no token field
 * so settings storage can never leak a credential.
 */
@Serializable
data class PairingMetadata(
    val id: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
    val webStorageMode: WebStorageMode = WebStorageMode.PERSISTENT,
    val webTrustedTls: Boolean = false,
    val lastConnectedAt: Long? = null,
)
