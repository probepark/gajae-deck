package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.BridgeSessionClient
import io.devnogari.gajaedeck.bridge.KtorBridgeConnector
import io.devnogari.gajaedeck.bridge.bridgeBaseUrl
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.control.SessionRoute
import io.devnogari.gajaedeck.control.TransientRouteHandoff
import kotlinx.coroutines.CoroutineScope

class ControlSessionControllerFactory(
    private val repository: ControlPlaneRepository,
    private val controlPlaneClient: ControlPlaneClient,
    private val bridgeConnectorFactory: (SessionRoute) -> BridgeConnector,
    private val transientRouteHandoff: TransientRouteHandoff,
) {
    constructor(
        repository: ControlPlaneRepository,
        controlPlaneClient: ControlPlaneClient,
        bridgeConnectorFactory: (SessionRoute) -> BridgeConnector,
    ) : this(repository, controlPlaneClient, bridgeConnectorFactory, TransientRouteHandoff())
    constructor(
        repository: ControlPlaneRepository,
        controlPlaneClient: ControlPlaneClient,
        transientRouteHandoff: TransientRouteHandoff = TransientRouteHandoff(),
    ) : this(
        repository = repository,
        controlPlaneClient = controlPlaneClient,
        bridgeConnectorFactory = { route ->
            KtorBridgeConnector(
                baseUrl = route.bridgeBaseUrl(),
                token = route.scopedToken,
                ownerToken = route.ownerToken,
            )
        },
        transientRouteHandoff = transientRouteHandoff,
    )

    suspend fun forSession(
        controlPlaneId: String,
        sessionId: String,
        scope: CoroutineScope,
    ): SessionController {
        val route = transientRouteHandoff.consume(sessionId)
            ?: controlPlaneClient.respawnSession(sessionId).getOrThrow().also { repository.cacheRoute(controlPlaneId, it) }
        return controllerForRoute(route, scope)
    }

    suspend fun startForProject(
        controlPlaneId: String,
        projectId: String,
        scope: CoroutineScope,
        resume: String = "latest",
        scopes: List<String> = emptyList(),
    ): Pair<String, SessionController> {
        val route = controlPlaneClient.startSession(projectId, resume, scopes).getOrThrow()
        transientRouteHandoff.put(route)
        return route.sessionId to controllerForRoute(route, scope)
    }

    fun rememberStartedRoute(route: SessionRoute) {
        transientRouteHandoff.put(route)
    }

    private fun controllerForRoute(route: SessionRoute, scope: CoroutineScope): SessionController {
        val client = BridgeSessionClient(route, connectorFactory = bridgeConnectorFactory)
        return SessionController(
            connector = client.connector,
            scope = scope,
            redactor = client.redactor,
        )
    }
}
