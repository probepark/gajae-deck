package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.control.SessionRoute

/** Owns a bridge connector derived from a control-plane session route. */
class BridgeSessionClient(
    initialRoute: SessionRoute,
    initialLastSeq: Long = 0L,
    private val connectorFactory: (SessionRoute) -> BridgeConnector = { route ->
        KtorBridgeConnector(
            baseUrl = route.bridgeBaseUrl(),
            token = route.scopedToken,
            ownerToken = route.ownerToken,
        )
    },
) {
    private var route: SessionRoute = initialRoute
    private var bridgeConnector: BridgeConnector = connectorFactory(initialRoute)

    var lastSeq: Long = initialLastSeq
        private set

    val connector: BridgeConnector
        get() = bridgeConnector

    val redactor: Redactor
        get() = Redactor(setOfNotNull(route.scopedToken, route.ownerToken))

    fun reconnect(nextRoute: SessionRoute): BridgeConnector {
        route = nextRoute
        bridgeConnector = connectorFactory(nextRoute)
        return bridgeConnector
    }

    fun recordLastSeq(seq: Long) {
        if (seq > lastSeq) lastSeq = seq
    }
}

internal fun SessionRoute.bridgeBaseUrl(): String {
    require(routePath.startsWith("/s/")) { "session routePath must start with /s/" }
    require(!routePath.contains("..")) { "session routePath must not contain traversal" }
    return baseUrl.trimEnd('/') + routePath
}
