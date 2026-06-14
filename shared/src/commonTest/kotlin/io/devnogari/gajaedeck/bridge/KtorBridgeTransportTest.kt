package io.devnogari.gajaedeck.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private fun transport(
    handler: (path: String, authHeader: String?, idempotency: String?) -> Pair<String, HttpStatusCode>,
): KtorBridgeTransport {
    val engine = MockEngine { request ->
        val (body, status) = handler(
            request.url.encodedPath,
            request.headers[HttpHeaders.Authorization],
            request.headers["Idempotency-Key"],
        )
        respond(body, status, jsonHeaders)
    }
    return KtorBridgeTransport(
        baseUrl = "https://host:4077",
        token = "secret-token",
        sessionId = "S1",
        client = HttpClient(engine),
    )
}

class KtorBridgeTransportTest {

    @Test
    fun healthOk() = runTest {
        val t = transport { path, _, _ ->
            if (path == "/healthz") "{\"status\":\"ok\"}" to HttpStatusCode.OK else "{}" to HttpStatusCode.NotFound
        }
        assertTrue(t.health())
    }

    @Test
    fun handshakeAcceptedV2() = runTest {
        var sawAuth: String? = null
        val t = transport { path, auth, _ ->
            sawAuth = auth
            if (path == "/v1/handshake") {
                "{\"status\":\"accepted\",\"protocol_version\":2,\"session_id\":\"S1\",\"accepted_scopes\":[\"prompt\"]}" to HttpStatusCode.OK
            } else {
                "{}" to HttpStatusCode.NotFound
            }
        }
        val result = t.handshake(
            BridgeHandshakeRequest(ProtocolVersionRange(1, 2), listOf("events"), listOf("prompt")),
        )
        assertTrue(result is BridgeHandshakeResult.Accepted)
        assertEquals(2, result.value.protocolVersion)
        assertEquals("Bearer secret-token", sawAuth)
    }

    @Test
    fun postCommandSuccessSendsIdempotencyKey() = runTest {
        var sawKey: String? = null
        val t = transport { path, _, key ->
            sawKey = key
            if (path == "/v1/sessions/S1/commands") {
                "{\"type\":\"response\",\"command\":\"get_session_stats\",\"success\":true}" to HttpStatusCode.OK
            } else {
                "{}" to HttpStatusCode.NotFound
            }
        }
        val resp = t.postCommand("get_session_stats", idempotencyKey = "idem-1")
        assertTrue(resp.success)
        assertEquals("get_session_stats", resp.command)
        assertEquals("idem-1", sawKey)
    }

    @Test
    fun scopeDeniedMapsToTypedError() = runTest {
        val t = transport { path, _, _ ->
            if (path == "/v1/sessions/S1/commands") {
                "{\"error\":\"scope_denied\",\"scope\":\"bash\"}" to HttpStatusCode.Forbidden
            } else {
                "{}" to HttpStatusCode.NotFound
            }
        }
        val ex = assertFailsWith<BridgeException> { t.postCommand("bash", idempotencyKey = "k") }
        assertEquals(BridgeErrorCode.SCOPE_DENIED, ex.code)
        assertEquals("bash", ex.scope)
    }

    @Test
    fun unauthorizedMapsToTypedError() = runTest {
        val t = transport { _, _, _ -> "{\"error\":\"unauthorized\"}" to HttpStatusCode.Unauthorized }
        val ex = assertFailsWith<BridgeException> { t.postCommand("prompt", idempotencyKey = "k") }
        assertEquals(BridgeErrorCode.UNAUTHORIZED, ex.code)
    }

    @Test
    fun eventsStreamParsesFrames() = runTest {
        val body = "{\"type\":\"ready\",\"seq\":1}\n{\"type\":\"event\",\"seq\":2}\n"
        val t = transport { path, _, _ ->
            if (path == "/v1/sessions/S1/events") body to HttpStatusCode.OK else "{}" to HttpStatusCode.NotFound
        }
        val frames = t.events(lastSeq = 0).toList()
        assertEquals(2, frames.size)
        assertEquals(BridgeFrameType.READY, frames[0].type)
        assertEquals(2L, frames[1].seq)
    }
}
