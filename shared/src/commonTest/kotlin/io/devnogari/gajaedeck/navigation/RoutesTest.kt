package io.devnogari.gajaedeck.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {
    @Test
    fun sessionRouteRoundTripsBySessionId() {
        val route = SessionRoute("session-123")
        val encoded = Json.encodeToString(SessionRoute.serializer(), route)
        assertEquals(route, Json.decodeFromString(SessionRoute.serializer(), encoded))
        assertTrue(encoded.contains("session-123"))
    }

    @Test
    fun projectSessionsRouteRoundTripsByProjectId() {
        val route = ProjectSessionsRoute("project-123")
        val encoded = Json.encodeToString(ProjectSessionsRoute.serializer(), route)
        assertEquals(route, Json.decodeFromString(ProjectSessionsRoute.serializer(), encoded))
        assertTrue(encoded.contains("project-123"))
    }

    @Test
    fun parameterlessRoutesAreStableSingletons() {
        assertEquals(
            ProjectsRoute,
            Json.decodeFromString(ProjectsRoute.serializer(), Json.encodeToString(ProjectsRoute.serializer(), ProjectsRoute)),
        )
        assertEquals(
            PairingsListRoute,
            Json.decodeFromString(PairingsListRoute.serializer(), Json.encodeToString(PairingsListRoute.serializer(), PairingsListRoute)),
        )
        assertEquals(
            SettingsRoute,
            Json.decodeFromString(SettingsRoute.serializer(), Json.encodeToString(SettingsRoute.serializer(), SettingsRoute)),
        )
    }

    @Test
    fun sessionRouteCarriesNoSecret() {
        val encoded = Json.encodeToString(SessionRoute.serializer(), SessionRoute("s1"))
        assertFalse(encoded.contains("token"), "route must not embed a token: $encoded")
        assertFalse(encoded.contains("owner"), "route must not embed owner token: $encoded")
    }
}
