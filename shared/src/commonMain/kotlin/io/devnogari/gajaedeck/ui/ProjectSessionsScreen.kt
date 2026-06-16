package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
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
            Button(onClick = {
                scope.launch {
                    controlPlaneClient.startSession(projectId, resume = "latest", scopes = emptyList()).fold(
                        onSuccess = onSessionStarted,
                        onFailure = { error = it.message ?: "Failed to start session" },
                    )
                }
            }, shape = MaterialTheme.shapes.small) { Text("Start session") }
            FilledTonalButton(onClick = {
                scope.launch {
                    controlPlaneClient.startSession(projectId, resume = "new", scopes = emptyList()).fold(
                        onSuccess = onSessionStarted,
                        onFailure = { error = it.message ?: "Failed to start new conversation" },
                    )
                }
            }, shape = MaterialTheme.shapes.small) { Text("New conversation") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (val result = sessions) {
            null -> Text("Loading sessions…")
            else -> result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) Text("No sessions") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rows, key = { it.id }) { session ->
                            SessionRow(session = session, onClick = { onSessionSelected(session.id) })
                        }
                    }
                },
                onFailure = { Text("Failed to load sessions: ${it.message ?: "unknown"}") },
            )
        }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(session.id, style = MaterialTheme.typography.titleMedium)
                Text("seq ${session.lastSeq}", style = MaterialTheme.typography.bodySmall)
            }
            Text(session.status.name.lowercase())
        }
    }
}
