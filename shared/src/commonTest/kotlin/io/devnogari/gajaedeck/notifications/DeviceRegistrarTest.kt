package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.DeviceRegistration
import io.devnogari.gajaedeck.control.HealthResponse
import io.devnogari.gajaedeck.control.MetricsResponse
import io.devnogari.gajaedeck.control.Project
import io.devnogari.gajaedeck.control.Session
import io.devnogari.gajaedeck.control.SessionRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceRegistrarTest {
    @Test
    fun registerCallsControlPlaneWithPushToken() = runTest {
        val control = FakeControlPlaneClient()
        val registration = registration(pushToken = null)

        val result = registerWithPlatformPushToken(control, registration, FakePushTokenProvider("push_alias_123"))

        assertTrue(result.isSuccess)
        assertEquals("push_alias_123", control.registered?.pushToken)
        assertEquals("install_1", control.registered?.installId)
        assertEquals(listOf("gate_notifications"), control.registered?.capabilities)
    }

    @Test
    fun registerFailsHonestlyWhenPushTokenIsUnavailable() = runTest {
        val control = FakeControlPlaneClient()

        val result = registerWithPlatformPushToken(control, registration(), FakePushTokenProvider(null))

        assertTrue(result.isFailure)
        assertEquals("no push token (real provider deferred)", result.exceptionOrNull()?.message)
        assertNull(control.registered)
    }
}

private class FakePushTokenProvider(private val token: String?) : PushTokenProvider {
    override suspend fun currentToken(): String? = token
}

internal class FakeControlPlaneClient(
    private val sessions: List<Session> = emptyList(),
    private val route: SessionRoute = route(),
) : ControlPlaneClient {
    var registered: DeviceRegistration? = null
    var getSessionsProjectId: String? = null
    var respawnSessionId: String? = null

    override suspend fun getProjects(): Result<List<Project>> = Result.success(emptyList())
    override suspend fun getProject(id: String): Result<Project> = error("unused")
    override suspend fun getSessions(projectId: String): Result<List<Session>> {
        getSessionsProjectId = projectId
        return Result.success(sessions)
    }
    override suspend fun startSession(projectId: String, resume: String, scopes: List<String>): Result<SessionRoute> = error("unused")
    override suspend fun stopSession(sessionId: String): Result<Session> = error("unused")
    override suspend fun respawnSession(sessionId: String): Result<SessionRoute> {
        respawnSessionId = sessionId
        return Result.success(route)
    }
    override suspend fun registerDevice(reg: DeviceRegistration): Result<DeviceRegistration> {
        registered = reg
        return Result.success(reg)
    }
    override suspend fun health(): Result<HealthResponse> = error("unused")
    override suspend fun metrics(): Result<MetricsResponse> = error("unused")
}

internal fun registration(pushToken: String? = "push_alias_123") = DeviceRegistration(
    deviceId = "install_1",
    installId = "install_1",
    platform = "desktop",
    environment = "test",
    pushToken = pushToken,
    appVersion = "1.0.0",
    locale = "en-US",
    capabilities = listOf("gate_notifications"),
)

internal fun session(id: String = "sess_opaque", lastSeq: Long = 41L) = Session(
    id = id,
    projectId = "proj_opaque",
    routeIdHash = "route_hash",
    status = io.devnogari.gajaedeck.control.SessionStatus.READY,
    scopes = listOf("prompt"),
    startedAt = "2026-06-14T00:00:00Z",
    lastSeq = lastSeq,
)

internal fun route(sessionId: String = "sess_opaque") = SessionRoute(
    sessionId = sessionId,
    projectId = "proj_opaque",
    routeId = "route_opaque",
    routePath = "/s/route_opaque",
    baseUrl = "https://control.example.invalid",
    scopedToken = "scoped_alias",
    scopes = listOf("prompt"),
    revocation = "respawn_required",
    ownerToken = "owner_alias",
)
