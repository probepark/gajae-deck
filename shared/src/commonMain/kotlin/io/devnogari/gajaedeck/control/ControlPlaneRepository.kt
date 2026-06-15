package io.devnogari.gajaedeck.control

import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.StoredControlPlane
import io.devnogari.gajaedeck.auth.StoredPairing
import kotlinx.serialization.Serializable

sealed interface ControlPlaneWriteResult {
    data class Success(val controlPlane: StoredControlPlane) : ControlPlaneWriteResult
    data class Failure(val error: ControlPlaneRepositoryError) : ControlPlaneWriteResult
}

enum class ControlPlaneRepositoryError {
    NOT_FOUND,
    DUPLICATE_ID,
    SECRET_WRITE_FAILED,
}

@Serializable
data class CachedSessionRoute(
    val sessionId: String,
    val projectId: String,
    val routeIdHash: String? = null,
    val routePath: String,
    val baseUrl: String,
    val scopes: List<String>,
    val expiresAt: String? = null,
    val revocation: String,
)

class ControlPlaneRepository(
    private val secureStore: SecureStore,
) {
    private val routeCache = LinkedHashMap<String, CachedSessionRoute>()

    suspend fun save(controlPlane: StoredControlPlane, replaceExisting: Boolean = true): ControlPlaneWriteResult {
        val prior = load(controlPlane.id)
        if (prior != null && !replaceExisting) return ControlPlaneWriteResult.Failure(ControlPlaneRepositoryError.DUPLICATE_ID)
        return runCatching { secureStore.save(controlPlane.toStoredPairing()) }
            .fold(
                onSuccess = {
                    routeCache.keys.filter { it.startsWith(controlPlane.id.cachePrefix()) }.forEach { routeCache.remove(it) }
                    controlPlane.routeCache.forEach { cacheRoute(controlPlane.id, it) }
                    ControlPlaneWriteResult.Success(controlPlane.copy(routeCache = cachedRoutes(controlPlane.id)))
                },
                onFailure = { ControlPlaneWriteResult.Failure(ControlPlaneRepositoryError.SECRET_WRITE_FAILED) },
            )
    }

    suspend fun load(id: String): StoredControlPlane? = secureStore.load(id.secureId())?.toStoredControlPlane(cachedRoutes(id))

    suspend fun list(): List<StoredControlPlane> = secureStore.list()
        .filter { it.id.startsWith(CONTROL_STORE_PREFIX) }
        .map { stored -> stored.toStoredControlPlane(cachedRoutes(stored.id.removePrefix(CONTROL_STORE_PREFIX))) }

    suspend fun delete(id: String) {
        secureStore.delete(id.secureId())
        routeCache.keys.filter { it.startsWith(id.cachePrefix()) }.forEach { routeCache.remove(it) }
    }

    fun cacheRoute(controlPlaneId: String, route: SessionRoute) {
        cacheRoute(
            controlPlaneId,
            CachedSessionRoute(
                sessionId = route.sessionId,
                projectId = route.projectId,
                routePath = route.routePath,
                baseUrl = route.baseUrl,
                scopes = route.scopes,
                expiresAt = route.expiresAt,
                revocation = route.revocation,
            ),
        )
    }

    fun cacheRoute(controlPlaneId: String, route: CachedSessionRoute) {
        routeCache[controlPlaneId.cachePrefix() + route.sessionId] = route
    }

    fun cachedRoute(controlPlaneId: String, sessionId: String): CachedSessionRoute? = routeCache[controlPlaneId.cachePrefix() + sessionId]

    fun cachedRoutes(controlPlaneId: String): List<CachedSessionRoute> = routeCache
        .filterKeys { it.startsWith(controlPlaneId.cachePrefix()) }
        .values
        .toList()
}

private const val CONTROL_STORE_PREFIX = "control:"

private fun String.secureId(): String = if (startsWith(CONTROL_STORE_PREFIX)) this else CONTROL_STORE_PREFIX + this
private fun String.cachePrefix(): String = "$this:"

private fun StoredControlPlane.toStoredPairing(): StoredPairing = StoredPairing(
    id = id.secureId(),
    displayName = displayName,
    baseUrl = supervisorBaseUrl,
    host = supervisorBaseUrl,
    port = 0,
    token = controlToken,
    ownerToken = trustPin,
    importedAt = importedAt,
)

private fun StoredPairing.toStoredControlPlane(routeCache: List<CachedSessionRoute>): StoredControlPlane = StoredControlPlane(
    id = id.removePrefix(CONTROL_STORE_PREFIX),
    supervisorBaseUrl = baseUrl,
    controlToken = token,
    trustPin = ownerToken,
    displayName = displayName,
    importedAt = importedAt,
    routeCache = routeCache,
)
