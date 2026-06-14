package io.devnogari.gajaedeck.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real end-to-end smoke against a live `gjc --mode bridge` (self-signed TLS).
 * Guarded: runs only when GJC_BRIDGE_E2E=1 and GJC_BRIDGE_TOKEN is set, so normal builds skip it.
 * Proves the actual Kotlin client (KtorBridgeTransport over OkHttp) talks v2 to the real bridge.
 */
class LiveBridgeE2eTest {

    private fun trustAllClient(): HttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), null) }
        return HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(ssl.socketFactory, trustAll)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
    }

    @Test
    fun liveHealthHandshakeCommandEvents() = runBlocking {
        if (System.getenv("GJC_BRIDGE_E2E") != "1") return@runBlocking
        val token = System.getenv("GJC_BRIDGE_TOKEN") ?: return@runBlocking
        val base = System.getenv("GJC_BRIDGE_BASE") ?: "https://127.0.0.1:4077"
        val client = trustAllClient()

        val anon = KtorBridgeTransport(base, token, client = client)
        assertTrue(anon.health(), "healthz should be ok")

        val handshake = anon.handshake(
            BridgeHandshakeRequest(
                protocolVersionRange = ProtocolVersionRange(1, BRIDGE_PROTOCOL_VERSION),
                capabilities = listOf("events", "prompt", "permission", "workflow_gate"),
                requestedScopes = listOf("message:read", "prompt", "control"),
            ),
        )
        assertTrue(handshake is BridgeHandshakeResult.Accepted, "handshake should be accepted")
        val accepted = handshake.value
        assertEquals(2, accepted.protocolVersion)

        val session = KtorBridgeTransport(base, token, sessionId = accepted.sessionId, client = client)
        val resp = session.postCommand("get_session_stats", idempotencyKey = "e2e-" + kotlin.random.Random.nextInt())
        assertTrue(resp.success, "get_session_stats should succeed")
        // Events streaming (wire-format/incremental parsing) is covered by KtorBridgeTransportTest
        // (MockEngine) and the parser/timeline tests. A passive live session emits no unsolicited
        // frame until activity, so we do not block on events here.
        assertEquals("get_session_stats", resp.command)
    }
}
