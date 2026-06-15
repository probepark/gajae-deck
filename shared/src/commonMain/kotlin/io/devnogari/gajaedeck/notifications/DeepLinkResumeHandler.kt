package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.bridge.BridgeSessionClient
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.ui.ControlSessionControllerFactory
import io.devnogari.gajaedeck.ui.SessionController
import kotlinx.coroutines.CoroutineScope

/**
 * Resumes a session from a public deep link that carries only an opaque id.
 * The opaque id is matched against control-plane session ids; no tokens, paths, or raw URLs are accepted here.
 */
class DeepLinkResumeHandler(
    private val controlPlaneClient: ControlPlaneClient,
    private val controllerFactory: ControlSessionControllerFactory,
) {
    suspend fun resume(
        controlPlaneId: String,
        projectId: String,
        opaqueId: String,
        scope: CoroutineScope,
    ): Result<DeepLinkResumeResult> = runCatching {
        require(opaqueId.isNotBlank()) { "opaque notification id is required" }
        require(!opaqueId.contains('/')) { "deep-link id must be opaque" }
        require(!opaqueId.contains(':')) { "deep-link id must be opaque" }

        val session = controlPlaneClient.getSessions(projectId).getOrThrow()
            .firstOrNull { it.id == opaqueId }
            ?: error("no control session for opaque id")
        val route = controlPlaneClient.respawnSession(session.id).getOrThrow()
        val bridgeClient = BridgeSessionClient(route, initialLastSeq = session.lastSeq)
        controllerFactory.rememberStartedRoute(route)
        val controller = controllerFactory.forSession(controlPlaneId, session.id, scope)
        DeepLinkResumeResult(
            sessionId = session.id,
            lastSeq = session.lastSeq,
            controller = controller,
            bridgeClient = bridgeClient,
        )
    }
}

data class DeepLinkResumeResult(
    val sessionId: String,
    val lastSeq: Long,
    val controller: SessionController,
    val bridgeClient: BridgeSessionClient,
)
