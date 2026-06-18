package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.CommandGroup
import io.devnogari.gajaedeck.bridge.CommandRegistry
import io.devnogari.gajaedeck.bridge.ConnectionState
import io.devnogari.gajaedeck.bridge.FieldKind
import io.devnogari.gajaedeck.bridge.GateItem
import io.devnogari.gajaedeck.bridge.MessageItem
import io.devnogari.gajaedeck.bridge.NoticeItem
import io.devnogari.gajaedeck.bridge.ToolCallItem
import io.devnogari.gajaedeck.bridge.TranscriptItem
import kotlinx.serialization.json.JsonObject

/** Live remote-control screen: status, streamed transcript, pending gates, prompt input, command palette, and safe send log. */
@Composable
fun SessionScreen(controller: SessionController, onBack: () -> Unit) {
    val state by controller.state.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text("세션 목록") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Claude Code 원격 제어", style = MaterialTheme.typography.titleMedium)
                    Text(
                        connectionDescription(state.connection),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ConnectionStatus(state.connection)
            }
        }

        state.error?.let {
            Text(
                "세션 연결에 문제가 있습니다. Supervisor와 브리지 상태를 확인하세요.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.transcript.isEmpty()) {
                item {
                    EmptyTranscriptCard()
                }
            }
            items(state.transcript, key = { it.key }) { item ->
                TranscriptCard(item)
            }
        }

        if (state.gateRequests.isNotEmpty()) {
            state.gateRequests.forEach { req ->
                key(req.correlationId) {
                    val fieldValues = remember { mutableStateMapOf<String, String>() }
                    ActionRequestPanel(
                        request = req,
                        fieldValues = fieldValues,
                        onFieldChange = { name, value -> fieldValues[name] = value },
                        onAction = { actionId ->
                            controller.respondToGate(req.correlationId, actionId, fieldValues.toMap())
                            fieldValues.clear()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("원격 지시 입력") },
                    placeholder = { Text("데스크톱 Claude Code 세션에 보낼 메시지") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        controller.sendPrompt(prompt)
                        prompt = ""
                    },
                    enabled = prompt.isNotBlank() && state.connection == ConnectionState.CONNECTED_STREAMING,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("전송")
                }
            }
        }

        CommandPalette(state) { type, params -> controller.sendCommand(type, params) }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("전송 로그", style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState())) {
                    state.sentLog.takeLast(8).forEach { line ->
                        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTranscriptCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("대기 중", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                "세션 이벤트가 도착하면 여기에 대화, 도구 실행, 승인 요청이 시간순으로 표시됩니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TranscriptCard(item: TranscriptItem) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (item) {
                is MessageItem -> {
                    Text(roleLabel(item.role), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(item.text, style = MaterialTheme.typography.bodySmall)
                }

                is ToolCallItem -> {
                    Text("도구 실행 · ${item.tool}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }

                is GateItem -> {
                    Text("입력 필요 · ${gateKindLabel(item.frameKind)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(item.preview, style = MaterialTheme.typography.bodySmall)
                }

                is NoticeItem -> {
                    Text("시스템 · ${item.kind}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONNECTED_STREAMING -> "연결됨" to MaterialTheme.colorScheme.primary
        ConnectionState.CHECKING_HEALTH -> "연결 확인" to MaterialTheme.colorScheme.secondary
        ConnectionState.REPLAYING -> "동기화 중" to MaterialTheme.colorScheme.secondary
        ConnectionState.BACKOFF_RECONNECTING -> "재연결 중" to MaterialTheme.colorScheme.secondary
        ConnectionState.PAIRING -> "페어링 중" to MaterialTheme.colorScheme.secondary
        ConnectionState.DISCONNECTED_BY_USER -> "연결 해제" to MaterialTheme.colorScheme.secondary
        ConnectionState.DESYNCED -> "동기화 오류" to MaterialTheme.colorScheme.error
        ConnectionState.AUTH_BLOCKED,
        ConnectionState.TLS_BLOCKED,
        ConnectionState.CORS_BLOCKED,
        ConnectionState.PROTOCOL_BLOCKED,
        ConnectionState.ENDPOINT_DISABLED,
        -> "차단됨" to MaterialTheme.colorScheme.error
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Scope-aware command launcher kept secondary to the primary chat/gate flow. */
@Composable
private fun CommandPalette(state: SessionUiState, onSend: (String, JsonObject) -> Unit) {
    var selected by remember { mutableStateOf<io.devnogari.gajaedeck.bridge.CommandMetadata?>(null) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("원격 제어 도구", style = MaterialTheme.typography.titleSmall)
            CommandRegistry.exposed.groupBy { it.group }.forEach { (group, cmds) ->
                Text(commandGroupLabel(group), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    cmds.forEach { meta ->
                        val enabled = CommandRegistry.isEnabled(meta.type, state.grantedScopes)
                        FilledTonalButton(
                            enabled = enabled,
                            shape = MaterialTheme.shapes.small,
                            onClick = {
                                if (meta.fields.isEmpty()) {
                                    onSend(meta.type, JsonObject(emptyMap()))
                                } else {
                                    selected = meta
                                    fieldValues.clear()
                                }
                            },
                        ) {
                            Text(commandLabel(meta.type))
                        }
                    }
                }
            }

            selected?.let { meta ->
                val requiredFilled = meta.fields.filter { it.required }.all { (fieldValues[it.name] ?: "").isNotBlank() }
                val canSend = CommandRegistry.isEnabled(meta.type, state.grantedScopes) && requiredFilled
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(commandLabel(meta.type), fontWeight = FontWeight.Bold)
                        Text(meta.type, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        meta.fields.forEach { field ->
                            when (field.kind) {
                                FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(field.label, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = fieldValues[field.name] == "true",
                                        onCheckedChange = { fieldValues[field.name] = it.toString() },
                                    )
                                }

                                else -> OutlinedTextField(
                                    value = fieldValues[field.name] ?: "",
                                    onValueChange = { fieldValues[field.name] = it },
                                    label = { Text(field.label + if (field.required) " *" else "") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = canSend,
                                shape = MaterialTheme.shapes.small,
                                onClick = {
                                    onSend(meta.type, CommandRegistry.buildParams(meta, fieldValues.toMap()))
                                    selected = null
                                    fieldValues.clear()
                                },
                            ) {
                                Text("전송")
                            }
                            TextButton(onClick = {
                                selected = null
                                fieldValues.clear()
                            }) {
                                Text("취소")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role.lowercase()) {
    "assistant" -> "Assistant"
    "user" -> "User"
    "system" -> "System"
    else -> role.replaceFirstChar { it.uppercase() }
}

private fun gateKindLabel(kind: String): String = when (kind) {
    "permission_request" -> "권한 승인"
    "workflow_gate" -> "워크플로 게이트"
    "ui_request" -> "추가 입력"
    "elicitation" -> "질문 응답"
    "host_uri_request" -> "호스트 URI"
    else -> kind
}

private fun connectionDescription(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED_STREAMING -> "데스크톱 세션의 대화와 승인 요청을 실시간으로 받는 중입니다."
    ConnectionState.CHECKING_HEALTH -> "Supervisor와 브리지 상태를 확인하는 중입니다."
    ConnectionState.REPLAYING -> "최근 대화 기록을 동기화하는 중입니다."
    ConnectionState.BACKOFF_RECONNECTING -> "연결이 끊겨 다시 연결을 시도하는 중입니다."
    ConnectionState.PAIRING -> "제어 평면 연결 정보를 확인하는 중입니다."
    ConnectionState.DISCONNECTED_BY_USER -> "사용자가 원격 연결을 종료했습니다."
    ConnectionState.DESYNCED -> "이벤트 순서가 어긋나 세션 동기화가 필요합니다."
    ConnectionState.AUTH_BLOCKED -> "토큰 또는 권한 문제로 연결이 차단되었습니다."
    ConnectionState.TLS_BLOCKED -> "TLS 신뢰 설정 때문에 연결이 차단되었습니다."
    ConnectionState.CORS_BLOCKED -> "브라우저 동일 출처 프록시 설정이 필요합니다."
    ConnectionState.PROTOCOL_BLOCKED -> "브리지 프로토콜 협상이 실패했습니다."
    ConnectionState.ENDPOINT_DISABLED -> "필요한 브리지 엔드포인트가 비활성화되어 있습니다."
}

private fun commandGroupLabel(group: CommandGroup): String = when (group) {
    CommandGroup.PROMPT -> "대화"
    CommandGroup.CONTROL -> "제어"
    CommandGroup.SESSION -> "세션"
    CommandGroup.MESSAGE_READ -> "기록"
    CommandGroup.MODEL -> "모델"
    CommandGroup.EXPORT -> "내보내기"
    CommandGroup.EXECUTION -> "실행"
    CommandGroup.HOST_TOOLS -> "호스트 도구"
    CommandGroup.HOST_URI -> "호스트 URI"
    CommandGroup.ADMIN -> "관리"
}

private fun commandLabel(type: String): String = when (type) {
    "steer" -> "지시 보내기"
    "follow_up" -> "후속 입력"
    "abort" -> "중단"
    "abort_and_prompt" -> "중단 후 지시"
    "new_session" -> "새 세션"
    "switch_session" -> "세션 전환"
    "get_state" -> "상태 확인"
    "get_messages" -> "대화 가져오기"
    "get_branch_messages" -> "브랜치 기록"
    "get_last_assistant_text" -> "마지막 응답"
    "get_session_stats" -> "세션 통계"
    "set_model" -> "모델 변경"
    "set_thinking_level" -> "추론 수준"
    "export_html" -> "HTML 내보내기"
    else -> type
}
