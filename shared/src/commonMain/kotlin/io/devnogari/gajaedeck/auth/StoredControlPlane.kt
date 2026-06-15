package io.devnogari.gajaedeck.auth

import io.devnogari.gajaedeck.control.CachedSessionRoute
import kotlinx.serialization.Serializable

@Serializable
data class StoredControlPlane(
    val id: String,
    val supervisorBaseUrl: String,
    val controlToken: String,
    val trustPin: String? = null,
    val displayName: String,
    val importedAt: Long,
    val routeCache: List<CachedSessionRoute> = emptyList(),
) {
    fun secrets(): Set<String> = buildSet {
        add(controlToken)
    }
}
