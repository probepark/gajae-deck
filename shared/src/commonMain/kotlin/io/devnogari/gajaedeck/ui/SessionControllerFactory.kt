package io.devnogari.gajaedeck.ui

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.StoredPairing
import io.devnogari.gajaedeck.bridge.BridgeConnector
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.bridge.KtorBridgeConnector
import kotlinx.coroutines.CoroutineScope

/**
 * Builds a [SessionController] for a saved pairing: loads the secret from [SecureStore], constructs
 * the connector (live Ktor when a token exists, offline fake otherwise), and seeds a [Redactor] with
 * the pairing's real token/ownerToken so live logs and UI errors redact the actual credential.
 * The token is loaded here and never travels through navigation.
 */
class SessionControllerFactory(
    private val secureStore: SecureStore,
    private val connectorFactory: (StoredPairing) -> BridgeConnector = ::defaultConnector,
) {
    suspend fun create(pairingId: String, scope: CoroutineScope): SessionController? {
        val pairing = secureStore.load(pairingId) ?: return null
        val redactor = Redactor(setOfNotNull(pairing.token.ifBlank { null }, pairing.ownerToken))
        return SessionController(connector = connectorFactory(pairing), scope = scope, redactor = redactor)
    }
}

private fun defaultConnector(pairing: StoredPairing): BridgeConnector =
    if (pairing.token.isBlank()) {
        FakeBridgeConnector(FakeBridgeTransport(frames = demoFrames()))
    } else {
        KtorBridgeConnector(baseUrl = pairing.baseUrl, token = pairing.token, ownerToken = pairing.ownerToken)
    }

private fun demoFrames(): List<BridgeFrame> = listOfNotNull(
    BridgeStreamParser.parseFrame("""{"type":"ready","seq":1,"protocol_version":2}"""),
    BridgeStreamParser.parseFrame("""{"type":"event","seq":2,"role":"assistant","text":"Hello from gjc"}"""),
    BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":3,"tool":"bash","correlation_id":"c1"}"""),
)
