package io.devnogari.gajaedeck.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {

    @Test
    fun sessionRouteRoundTripsByPairingId() {
        val route = SessionRoute("host:4077")
        val encoded = Json.encodeToString(SessionRoute.serializer(), route)
        assertEquals(route, Json.decodeFromString(SessionRoute.serializer(), encoded))
        assertTrue(encoded.contains("host:4077"))
    }

    @Test
    fun parameterlessRoutesAreStableSingletons() {
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
        // Type-safe routes must carry only a pairing id, never a token/secret (it would leak into the backstack).
        val encoded = Json.encodeToString(SessionRoute.serializer(), SessionRoute("p1"))
        assertFalse(encoded.contains("token"), "route must not embed a token: $encoded")
    }
}
