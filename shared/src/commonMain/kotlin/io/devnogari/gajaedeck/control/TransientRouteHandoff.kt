package io.devnogari.gajaedeck.control

/**
 * Process-local handoff for newly started routes that still contain bearer tokens.
 *
 * The UI navigates only with the session id; this holder carries the full route to the
 * controller factory without writing tokens into navigation arguments or durable route cache.
 */
class TransientRouteHandoff {
    private val routesBySessionId = mutableMapOf<String, SessionRoute>()

    fun put(route: SessionRoute) {
        routesBySessionId[route.sessionId] = route
    }

    fun consume(sessionId: String): SessionRoute? = routesBySessionId.remove(sessionId)

    fun clear() {
        routesBySessionId.clear()
    }
}
