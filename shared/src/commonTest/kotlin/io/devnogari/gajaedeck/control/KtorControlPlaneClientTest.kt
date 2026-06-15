package io.devnogari.gajaedeck.control

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class KtorControlPlaneClientTest {
    @Test
    fun startSessionSendsResumeModeAndDecodesRoute() = runTest {
        var request: HttpRequestData? = null
        val client = client { req ->
            request = req
            fixtureSessionStart to HttpStatusCode.OK
        }

        val route = client.startSession("proj_7f3a", resume = "new", scopes = listOf("prompt")).getOrThrow()

        assertEquals("/control/v1/projects/proj_7f3a/sessions", request?.url?.encodedPath)
        val body = Json.parseToJsonElement(request?.bodyText().orEmpty()).jsonObject
        assertEquals("new", body["resume"]?.jsonPrimitive?.content)
        assertEquals("/s/route_opaque_7f3a", route.routePath)
    }

    @Test
    fun stopAndRespawnUseColonSuffixedPostPaths() = runTest {
        val paths = mutableListOf<String>()
        val methods = mutableListOf<HttpMethod>()
        val client = client { req ->
            paths += req.url.encodedPath
            methods += req.method
            when {
                req.url.encodedPath.endsWith(":stop") -> fixtureSessionStart to HttpStatusCode.OK
                req.url.encodedPath.endsWith(":respawn") -> fixtureSessionStart to HttpStatusCode.OK
                else -> error("unexpected path ${req.url.encodedPath}")
            }
        }

        client.stopSession("sess_7f3a").getOrThrow()
        client.respawnSession("sess_7f3a").getOrThrow()

        assertEquals(
            listOf("/control/v1/sessions/sess_7f3a:stop", "/control/v1/sessions/sess_7f3a:respawn"),
            paths,
        )
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Post), methods)
    }

    @Test
    fun decodesControlFixtures() {
        assertEquals(2, controlJson.decodeFromString(ProjectsResponse.serializer(), fixtureProjects).projects.size)
        assertEquals(2, controlJson.decodeFromString(SessionsResponse.serializer(), fixtureSessions).sessions.size)
        assertEquals("stale_ttl", controlJson.decodeFromString(SessionsResponse.serializer(), fixtureSessions).sessions[1].skipReason)
        assertEquals("sess_7f3a", controlJson.decodeFromString(SessionResponse.serializer(), fixtureSessionStart).route?.sessionId)
        assertEquals("dev_hash_7f3a", controlJson.decodeFromString(DeviceRegistrationResponse.serializer(), fixtureDeviceRegister).deviceId)
        assertNotNull(controlJson.decodeFromString(MetricsResponse.serializer(), fixtureMetrics).counters["restore.skipped"])
        assertEquals("degraded", controlJson.decodeFromString(HealthResponse.serializer(), fixtureHealth).status)
    }

    @Test
    fun registerDeviceSendsRequestDtoAndReadsTopLevelResponse() = runTest {
        var request: HttpRequestData? = null
        val client = client { req ->
            request = req
            fixtureDeviceRegister to HttpStatusCode.OK
        }

        val registration = client.registerDevice(
            DeviceRegistration(
                deviceId = "dev_hash_7f3a",
                installId = "install_7f3a",
                pushToken = "push",
                platform = "desktop",
                environment = "dev",
                appVersion = "1.2.3",
                locale = "ko-KR",
                capabilities = listOf("push", "gates"),
            ),
        ).getOrThrow()

        assertEquals("/control/v1/devices", request?.url?.encodedPath)
        val body = Json.parseToJsonElement(request?.bodyText().orEmpty()).jsonObject
        assertEquals("install_7f3a", body["installId"]?.jsonPrimitive?.content)
        assertEquals("desktop", body["platform"]?.jsonPrimitive?.content)
        assertEquals("dev", body["environment"]?.jsonPrimitive?.content)
        assertEquals("push", body["pushToken"]?.jsonPrimitive?.content)
        assertEquals("1.2.3", body["appVersion"]?.jsonPrimitive?.content)
        assertEquals("dev_hash_7f3a", registration.deviceId)
        assertEquals("2026-06-14T16:20:00Z", registration.registeredAt)
    }

    private fun client(handler: suspend (HttpRequestData) -> Pair<String, HttpStatusCode>): KtorControlPlaneClient {
        val engine = MockEngine { request ->
            val (body, status) = handler(request)
            respond(body, status, jsonHeaders)
        }
        return KtorControlPlaneClient("https://control.example.invalid", "control-token", HttpClient(engine))
    }
}

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> content.toString()
}

private const val fixtureProjects = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000000000","serverTime":"2026-06-14T16:20:00Z","projects":[{"id":"proj_7f3a","displayAlias":"project_alias_alpha","cwdHash":"hash_cwd_7f3a","status":"running","lastActiveAt":"2026-06-14T16:18:30Z","lastSessionId":"sess_7f3a","autostart":"recent","health":"ok","safeSummaryAlias":"summary_alias_active"},{"id":"proj_91bd","displayAlias":"project_alias_beta","cwdHash":"hash_cwd_91bd","status":"stopped","lastActiveAt":"2026-06-14T14:12:00Z","lastSessionId":null,"autostart":"manual","health":"ok","safeSummaryAlias":"summary_alias_stopped"}]}"""

private const val fixtureSessions = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000000000","serverTime":"2026-06-14T16:20:00Z","sessions":[{"id":"sess_7f3a","projectId":"proj_7f3a","routeIdHash":"hash_route_7f3a","gjcSessionId":"gjc_sess_7f3a","status":"ready","scopes":["events","prompt","permission","ui_responses","claim_control"],"startedAt":"2026-06-14T15:59:00Z","lastActiveAt":"2026-06-14T16:18:30Z","lastSeq":42,"bridgePid":4242,"restoreEligibility":"eligible"},{"id":"sess_91bd","projectId":"proj_7f3a","routeIdHash":"hash_route_91bd","gjcSessionId":"gjc_sess_7f3a","status":"idle","scopes":["events","prompt","permission","ui_responses","claim_control"],"startedAt":"2026-06-14T15:59:00Z","lastActiveAt":"2026-06-14T16:18:30Z","lastSeq":108,"bridgePid":null,"restoreEligibility":"skipped","skipReason":"stale_ttl"}]}"""

private const val fixtureSessionStart = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000000000","serverTime":"2026-06-14T16:20:00Z","session":{"id":"sess_7f3a","projectId":"proj_7f3a","routeIdHash":"hash_route_7f3a","gjcSessionId":"gjc_sess_7f3a","status":"ready","scopes":["events","prompt","permission","ui_responses","claim_control"],"startedAt":"2026-06-14T15:59:00Z","lastActiveAt":"2026-06-14T16:18:30Z","lastSeq":42,"bridgePid":4242,"restoreEligibility":"eligible"},"route":{"sessionId":"sess_7f3a","projectId":"proj_7f3a","routeId":"route_opaque_7f3a","routePath":"/s/route_opaque_7f3a","baseUrl":"https://control.example.invalid","scopedToken":"scoped_route_token_alias_7f3a","scopes":["events","prompt","permission","ui_responses","claim_control"],"expiresAt":null,"revocation":"respawn_required","ownerToken":"owner_token_alias_7f3a"}}"""

private const val fixtureDeviceRegister = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000000000","serverTime":"2026-06-14T16:20:00Z","deviceId":"dev_hash_7f3a","registeredAt":"2026-06-14T16:20:00Z"}"""

private const val fixtureMetrics = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000000000","serverTime":"2026-06-14T16:20:00Z","counters":{"restore.attempt":3,"restore.success":2,"restore.skipped":{"stale_ttl":1,"crash_loop_cap":1},"route.reject":{"route_token_mismatch":1,"scope_denied":1},"crash_loop.backoff":2,"crash_loop.cap":1,"push.sent":{"apns":4,"fcm":3},"push.failed":{"apns":1,"fcm":0},"session.start":3,"session.stop":1,"session.respawn":1,"gate.resolved":5},"gauges":{"provider.credential_health":{"apns":1,"fcm":0},"bridge.ready":1,"bridge.idle":1,"bridge.degraded":0},"labels":{"projectHash":"hash_project_7f3a","sessionHash":"hash_session_7f3a","routeHash":"hash_route_7f3a","collapseIdHash":"col_h_4f6e2a91c0bd"}}"""

private const val fixtureHealth = """{"schemaVersion":1,"requestId":"req_01JSAFE000000000000","serverTime":"2026-06-14T16:20:00Z","status":"degraded","supervisor":{"status":"ok"},"config":{"status":"ok"},"provider":{"status":"degraded","code":"provider_unhealthy"},"store":{"status":"ok"},"bridges":{"ready":1,"idle":1,"degraded":0,"stopped":1}}"""
