package io.devnogari.gajaedeck.bridge

import io.devnogari.gajaedeck.control.SessionRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class BridgeSessionClientTest {
    @Test
    fun createsConnectorFromSessionRouteAndRedactsRouteTokens() {
        val connector = FakeBridgeConnector(FakeBridgeTransport())
        val route = route(scopedToken = "scoped-secret", ownerToken = "owner-secret")
        val client = BridgeSessionClient(route, initialLastSeq = 7L) { connector }

        assertSame(connector, client.connector)
        assertEquals(7L, client.lastSeq)
        assertEquals("*** and ***", client.redactor.redact("scoped-secret and owner-secret"))
    }

    @Test
    fun reconnectReplacesConnectorButKeepsObservedLastSeq() {
        val first = FakeBridgeConnector(FakeBridgeTransport())
        val second = FakeBridgeConnector(FakeBridgeTransport())
        var calls = 0
        val client = BridgeSessionClient(route(), initialLastSeq = 2L) {
            calls += 1
            if (calls == 1) first else second
        }

        client.recordLastSeq(9L)
        val reconnected = client.reconnect(route(sessionId = "s2", scopedToken = "next"))

        assertSame(second, reconnected)
        assertSame(second, client.connector)
        assertNotSame(first, client.connector)
        assertEquals(9L, client.lastSeq)
    }

    @Test
    fun bridgeBaseUrlCombinesBaseUrlWithSessionRoutePath() {
        assertEquals("http://127.0.0.1:4077/s/r1", route().bridgeBaseUrl())
        assertEquals("http://127.0.0.1:4077/s/r1", route(baseUrl = "http://127.0.0.1:4077/").bridgeBaseUrl())
    }

    @Test
    fun bridgeBaseUrlRejectsNonSessionAndTraversalRoutePaths() {
        assertFailsWith<IllegalArgumentException> { route(routePath = "/x/r1").bridgeBaseUrl() }
        assertFailsWith<IllegalArgumentException> { route(routePath = "/s/../secret").bridgeBaseUrl() }
    }

    private fun route(
        sessionId: String = "s1",
        scopedToken: String = "scoped",
        ownerToken: String? = "owner",
        routePath: String = "/s/r1",
        baseUrl: String = "http://127.0.0.1:4077",
    ) = SessionRoute(
        sessionId = sessionId,
        projectId = "p1",
        routeId = "r1",
        routePath = routePath,
        baseUrl = baseUrl,
        scopedToken = scopedToken,
        scopes = listOf("prompt"),
        expiresAt = null,
        revocation = "revocation",
        ownerToken = ownerToken,
    )
}
