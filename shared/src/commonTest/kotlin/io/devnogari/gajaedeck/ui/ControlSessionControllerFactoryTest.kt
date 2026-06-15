package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.auth.InMemorySecureStore
import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.control.DeviceRegistration
import io.devnogari.gajaedeck.control.HealthResponse
import io.devnogari.gajaedeck.control.MetricsResponse
import io.devnogari.gajaedeck.control.Project
import io.devnogari.gajaedeck.control.Session
import io.devnogari.gajaedeck.control.SessionRoute
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ControlSessionControllerFactoryTest {
    @Test
    fun forSessionRespawnsRouteCachesItAndBuildsController() = runTest {
        val repository = ControlPlaneRepository(InMemorySecureStore())
        val route = route(sessionId = "s1")
        val control = FakeControlPlaneClient(respawnRoute = route)
        val connector = FakeBridgeConnector(FakeBridgeTransport())
        var connectorRoute: SessionRoute? = null
        val factory = ControlSessionControllerFactory(repository, control) { selectedRoute: SessionRoute ->
            connectorRoute = selectedRoute
            connector
        }

        val controller = factory.forSession("cp1", "s1", TestScope())

        assertNotNull(controller)
        assertEquals("s1", repository.cachedRoute("cp1", "s1")?.sessionId)
        assertEquals(route, connectorRoute)
    }

    @Test
    fun startForProjectStartsSessionCachesRouteAndReturnsSessionId() = runTest {
        val repository = ControlPlaneRepository(InMemorySecureStore())
        val route = route(sessionId = "started")
        val control = FakeControlPlaneClient(startRoute = route)
        val connector = FakeBridgeConnector(FakeBridgeTransport())
        var connectorRoute: SessionRoute? = null
        val factory = ControlSessionControllerFactory(repository, control) { selectedRoute: SessionRoute ->
            connectorRoute = selectedRoute
            connector
        }

        val (sessionId, controller) = factory.startForProject("cp1", "p1", TestScope(), resume = "new")

        assertEquals("started", sessionId)
        assertNotNull(controller)
        assertEquals(route, connectorRoute)
        assertEquals(null, repository.cachedRoute("cp1", "started")?.sessionId)
        assertEquals("new", control.lastResume)
    }

    @Test
    fun forSessionConsumesTransientRouteWithoutRespawn() = runTest {
        val repository = ControlPlaneRepository(InMemorySecureStore())
        val route = route(sessionId = "fresh")
        val control = FakeControlPlaneClient(respawnRoute = route(sessionId = "respawned"))
        var connectorRoute: SessionRoute? = null
        val handoff = io.devnogari.gajaedeck.control.TransientRouteHandoff()
        handoff.put(route)
        val connector = FakeBridgeConnector(FakeBridgeTransport())
        val factory = ControlSessionControllerFactory(repository, control, { selectedRoute ->
            connectorRoute = selectedRoute
            connector
        }, handoff)

        assertNotNull(factory.forSession("cp1", "fresh", TestScope()))

        assertEquals(0, control.respawnCalls)
        assertEquals(route, connectorRoute)
        assertEquals(null, repository.cachedRoute("cp1", "fresh")?.sessionId)
    }

    @Test
    fun forSessionRespawnsWhenTransientRouteIsMissing() = runTest {
        val repository = ControlPlaneRepository(InMemorySecureStore())
        val route = route(sessionId = "respawned")
        val control = FakeControlPlaneClient(respawnRoute = route)
        var connectorRoute: SessionRoute? = null
        val connector = FakeBridgeConnector(FakeBridgeTransport())
        val factory = ControlSessionControllerFactory(repository, control) { selectedRoute: SessionRoute ->
            connectorRoute = selectedRoute
            connector
        }

        assertNotNull(factory.forSession("cp1", "missing", TestScope()))

        assertEquals(1, control.respawnCalls)
        assertEquals(route, connectorRoute)
        assertEquals(route.sessionId, repository.cachedRoute("cp1", "respawned")?.sessionId)
    }

    private class FakeControlPlaneClient(
        private val startRoute: SessionRoute = route(sessionId = "start"),
        private val respawnRoute: SessionRoute = route(sessionId = "respawn"),
    ) : ControlPlaneClient {
        var lastResume: String? = null
        override suspend fun getProjects(): Result<List<Project>> = Result.success(emptyList())
        override suspend fun getProject(id: String): Result<Project> = error("unused")
        override suspend fun getSessions(projectId: String): Result<List<Session>> = Result.success(emptyList())
        override suspend fun startSession(projectId: String, resume: String, scopes: List<String>): Result<SessionRoute> {
            lastResume = resume
            return Result.success(startRoute)
        }
        override suspend fun stopSession(sessionId: String): Result<Session> = error("unused")
        var respawnCalls = 0

    override suspend fun respawnSession(sessionId: String): Result<SessionRoute> {
        respawnCalls += 1
        return Result.success(respawnRoute)
    }
        override suspend fun registerDevice(reg: DeviceRegistration): Result<DeviceRegistration> = Result.success(reg)
        override suspend fun health(): Result<HealthResponse> = error("unused")
        override suspend fun metrics(): Result<MetricsResponse> = error("unused")
    }

    private companion object {
        fun route(sessionId: String = "s1") = SessionRoute(
            sessionId = sessionId,
            projectId = "p1",
            routeId = "r1",
            routePath = "/s/r1",
            baseUrl = "http://127.0.0.1:4077",
            scopedToken = "scoped-$sessionId",
            scopes = listOf("prompt"),
            expiresAt = null,
            revocation = "revocation",
            ownerToken = "owner-$sessionId",
        )
    }
}
