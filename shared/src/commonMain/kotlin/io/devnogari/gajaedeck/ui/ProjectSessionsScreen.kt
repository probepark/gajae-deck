package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.Session
import io.devnogari.gajaedeck.control.SessionRoute
import io.devnogari.gajaedeck.control.SessionStatus
import kotlinx.coroutines.launch

private const val START_SESSION_ERROR_MESSAGE = "세션을 시작하지 못했습니다. 다시 시도하거나 Supervisor 연결 상태를 확인하세요."
private const val LOAD_SESSIONS_ERROR_MESSAGE = "세션 목록을 불러오지 못했습니다. 다시 시도하거나 Supervisor 연결 상태를 확인하세요."

@Composable
fun ProjectSessionsScreen(
    projectId: String,
    controlPlaneClient: ControlPlaneClient,
    onSessionSelected: (String) -> Unit,
    onSessionStarted: (SessionRoute) -> Unit = { onSessionSelected(it.sessionId) },
) {
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sessions = produceState<Result<List<Session>>?>(initialValue = null, projectId, refreshKey) {
        value = controlPlaneClient.getSessions(projectId)
    }.value

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("원격 세션", style = MaterialTheme.typography.titleLarge)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("데스크톱 Claude Code 세션을 브라우저에서 이어서 제어합니다.", fontWeight = FontWeight.Bold)
                Text(
                    "최근 세션을 이어가거나, 깨끗한 새 대화를 시작하세요. 화면에는 안전한 세션 별칭과 상태만 표시됩니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        controlPlaneClient.startSession(projectId, resume = "latest", scopes = emptyList()).fold(
                            onSuccess = onSessionStarted,
                            onFailure = { error = START_SESSION_ERROR_MESSAGE },
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("최근 세션 이어가기")
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        controlPlaneClient.startSession(projectId, resume = "new", scopes = emptyList()).fold(
                            onSuccess = onSessionStarted,
                            onFailure = { error = START_SESSION_ERROR_MESSAGE },
                        )
                    }
                    refreshKey += 1
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("새 세션 시작")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (val result = sessions) {
            null -> Text("세션을 불러오는 중…")
            else -> result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("아직 세션이 없습니다.", fontWeight = FontWeight.Bold)
                                Text(
                                    "새 세션 시작을 눌러 원격 제어 가능한 대화를 만드세요.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(rows, key = { _, session -> session.id }) { index, session ->
                                SessionRow(index = index + 1, session = session, onClick = { onSessionSelected(session.id) })
                            }
                        }
                    }
                },
                onFailure = {
                    Text(LOAD_SESSIONS_ERROR_MESSAGE, color = MaterialTheme.colorScheme.error)
                },
            )
        }
    }
}

@Composable
private fun SessionRow(index: Int, session: Session, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("세션 $index", style = MaterialTheme.typography.titleMedium)
                Text(
                    "대화 기록과 승인 요청을 이어서 엽니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sessionStatusLabel(session.status), style = MaterialTheme.typography.bodySmall)
                Text("열기", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun sessionStatusLabel(status: SessionStatus): String = when (status) {
    SessionStatus.STARTING -> "시작 중"
    SessionStatus.READY -> "준비됨"
    SessionStatus.RECONNECTING -> "재연결 중"
    SessionStatus.IDLE -> "유휴"
    SessionStatus.DEGRADED -> "주의 필요"
    SessionStatus.STOPPED -> "중지됨"
    SessionStatus.ERROR -> "오류"
}
