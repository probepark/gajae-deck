package io.devnogari.gajaedeck.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.devnogari.gajaedeck.bridge.BRIDGE_PROTOCOL_VERSION
import io.devnogari.gajaedeck.bridge.BridgeEndpointsDescriptor
import io.devnogari.gajaedeck.bridge.BridgeHandshakeAccepted
import io.devnogari.gajaedeck.bridge.BridgeHandshakeResult
import io.devnogari.gajaedeck.bridge.BridgeStreamParser
import io.devnogari.gajaedeck.bridge.FakeBridgeConnector
import io.devnogari.gajaedeck.bridge.FakeBridgeTransport
import io.devnogari.gajaedeck.theme.GajaeDeckTheme
import kotlinx.coroutines.CoroutineScope

fun demoSessionController(scope: CoroutineScope): SessionController =
    SessionController(
        connector = FakeBridgeConnector(
            FakeBridgeTransport(
                handshakeResult = BridgeHandshakeResult.Accepted(
                    BridgeHandshakeAccepted(
                        status = "accepted",
                        protocolVersion = BRIDGE_PROTOCOL_VERSION,
                        sessionId = "demo",
                        acceptedCapabilities = listOf(
                            "events",
                            "prompt",
                            "permission",
                            "workflow_gate",
                            "ui.declarative",
                            "elicitation",
                            "host_tools",
                            "host_uri",
                        ),
                        acceptedScopes = listOf(
                            "prompt",
                            "message:read",
                            "control",
                            "session",
                            "model",
                            "export",
                            "host_uri",
                        ),
                        endpoints = BridgeEndpointsDescriptor(
                            events = "/e",
                            commands = "/c",
                            uiResponses = "/u",
                            hostUriResults = "/h",
                        ),
                    ),
                ),
                frames = listOfNotNull(
                    BridgeStreamParser.parseFrame("""{"type":"ready","seq":1,"protocol_version":2}"""),
                    BridgeStreamParser.parseFrame("""{"type":"event","seq":2,"role":"assistant","text":"안녕하세요! gjc 원격 제어 데모입니다."}"""),
                    BridgeStreamParser.parseFrame("""{"type":"event","seq":3,"role":"assistant","text":"파일을 분석했습니다."}"""),
                    BridgeStreamParser.parseFrame("""{"type":"permission_request","seq":4,"tool":"bash","correlation_id":"c1"}"""),
                ),
            ),
        ),
        scope = scope,
    )

@Composable
fun DemoApp() {
    GajaeDeckTheme {
        val scope = rememberCoroutineScope()
        val controller = remember { demoSessionController(scope) }
        LaunchedEffect(controller) { controller.connect() }
        SessionScreen(controller = controller, onBack = {})
    }
}
