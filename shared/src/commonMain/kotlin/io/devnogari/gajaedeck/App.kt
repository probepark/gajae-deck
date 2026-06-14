package io.devnogari.gajaedeck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.bridge.KtorBridgeConnector
import io.devnogari.gajaedeck.ui.SessionController
import io.devnogari.gajaedeck.ui.SessionScreen

private fun sampleFrames(): List<BridgeFrame> = listOfNotNull(
    BridgeStreamParser.parseFrame("""{"type":"ready","seq":1,"protocol_version":2}"""),
    BridgeStreamParser.parseFrame("""{"type":"event","seq":2,"role":"assistant","text":"Hello from gjc"}"""),
    BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":3,"tool":"bash","correlation_id":"c1"}"""),
)

/**
 * Pre-DI app shell: manual pairing entry, then the split-out [SessionScreen]. The Koin composition
 * root + [io.devnogari.gajaedeck.navigation.AppNavHost] + [io.devnogari.gajaedeck.theme.GajaeDeckTheme]
 * wiring lands in G009; the session UI itself now lives in SessionScreen (no duplication).
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var controller by remember { mutableStateOf<SessionController?>(null) }

            val active = controller
            if (active == null) {
                PairingForm(onConnect = { host, port, token ->
                    val connector = if (token.isBlank()) {
                        FakeBridgeConnector(FakeBridgeTransport(frames = sampleFrames()))
                    } else {
                        KtorBridgeConnector(baseUrl = "https://$host:$port", token = token)
                    }
                    SessionController(connector, scope).also {
                        it.connect()
                        controller = it
                    }
                })
            } else {
                SessionScreen(active, onBack = { controller = null })
            }
        }
    }
}

@Composable
private fun PairingForm(onConnect: (host: String, port: String, token: String) -> Unit) {
    var host by remember { mutableStateOf("100.x.y.z") }
    var port by remember { mutableStateOf("4077") }
    var token by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("gajae-deck", style = MaterialTheme.typography.headlineMedium)
        Text("gjc Bridge 페어링 (수동 입력)")
        OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("Bearer token") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onConnect(host, port, token) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (token.isBlank()) "오프라인 데모 연결" else "연결")
        }
    }
}
