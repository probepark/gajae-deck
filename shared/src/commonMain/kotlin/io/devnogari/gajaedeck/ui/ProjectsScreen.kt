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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.Project

@Composable
fun ProjectsScreen(
    controlPlaneClient: ControlPlaneClient,
    onProjectSelected: (String) -> Unit,
) {
    val projects = produceState<Result<List<Project>>?>(initialValue = null, controlPlaneClient) {
        value = controlPlaneClient.getProjects()
    }.value

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Projects", style = MaterialTheme.typography.titleLarge)
        when (val result = projects) {
            null -> Text("Loading projects…")
            else -> result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) {
                        Text("No projects")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rows, key = { it.id }) { project ->
                                ProjectRow(project = project, onClick = { onProjectSelected(project.id) })
                            }
                        }
                    }
                },
                onFailure = { error -> Text("Failed to load projects: ${error.message ?: "unknown"}") },
            )
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(project.displayName, style = MaterialTheme.typography.titleMedium)
                Text(project.id, style = MaterialTheme.typography.bodySmall)
            }
            Text(project.status.name.lowercase())
        }
    }
}
