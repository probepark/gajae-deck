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
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.Session
import io.devnogari.gajaedeck.control.SessionRoute
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
        Text("Project sessions", style = MaterialTheme.typography.titleLarge)
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
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("새 세션 시작")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (val result = sessions) {
            null -> Text("Loading sessions…")
            else -> result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) {
                        Text("아직 세션이 없습니다. 새 세션 시작을 눌러 세션을 만드세요.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(rows, key = { _, session -> session.id }) { index, session ->
                                SessionRow(index = index + 1, onClick = { onSessionSelected(session.id) })
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
private fun SessionRow(index: Int, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("세션 $index")
            Text("선택한 세션 열기", style = MaterialTheme.typography.bodySmall)
        }
    }
}
