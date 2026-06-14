package io.devnogari.gajaedeck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.KtorBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.ui.SessionController

private val paletteCommands = listOf("get_session_stats", "get_messages", "compact", "abort")

private fun sampleFrames(): List<BridgeFrame> = listOfNotNull(
    BridgeStreamParser.parseFrame("""{"type":"ready","seq":1,"protocol_version":2}"""),
    BridgeStreamParser.parseFrame("""{"type":"event","seq":2,"role":"assistant","text":"Hello from gjc"}"""),
    BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":3,"tool":"bash","correlation_id":"c1"}"""),
)

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
                SessionView(active)
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
        Text(
            "메쉬(Tailscale/VPN)에서 도달 가능한 host:port + GJC_BRIDGE_TOKEN. 네이티브는 자체서명 TOFU, Web은 신뢰 인증서.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SessionView(controller: SessionController) {
    val state by controller.state.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("세션 — ${state.connection}", style = MaterialTheme.typography.titleMedium)
        state.error?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }

        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.frames) { frame -> FrameCard(frame) }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(prompt, { prompt = it }, label = { Text("프롬프트") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { controller.sendPrompt(prompt); prompt = "" }) { Text("전송") }
        }

        Text("명령 팔레트", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            paletteCommands.forEach { cmd ->
                Button(onClick = { controller.sendCommand(cmd) }) { Text(cmd) }
            }
        }

        Text("전송 로그", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
            state.sentLog.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun FrameCard(frame: BridgeFrame) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "#${frame.seq ?: "-"}  ${frame.type.wire}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(frame.raw.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
