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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.Project
import io.devnogari.gajaedeck.control.ProjectStatus

private const val LOAD_PROJECTS_ERROR_MESSAGE = "프로젝트 목록을 불러오지 못했습니다. Supervisor 연결 상태를 확인하세요."

@Composable
fun ProjectsScreen(
    controlPlaneClient: ControlPlaneClient,
    onProjectSelected: (String) -> Unit,
) {
    val projects = produceState<Result<List<Project>>?>(initialValue = null, controlPlaneClient) {
        value = controlPlaneClient.getProjects()
    }.value

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("원격 프로젝트", style = MaterialTheme.typography.titleLarge)
        Text(
            "Claude Code가 실행되는 로컬 프로젝트를 선택해 원격 세션을 이어가세요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        when (val result = projects) {
            null -> Text("프로젝트를 불러오는 중…")
            else -> result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("등록된 프로젝트가 없습니다.", fontWeight = FontWeight.Bold)
                                Text(
                                    "Supervisor 설정에 프로젝트를 추가한 뒤 다시 열어 주세요.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rows, key = { it.id }) { project ->
                                ProjectRow(project = project, onClick = { onProjectSelected(project.id) })
                            }
                        }
                    }
                },
                onFailure = {
                    Text(LOAD_PROJECTS_ERROR_MESSAGE, color = MaterialTheme.colorScheme.error)
                },
            )
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "안전 별칭으로 표시되는 원격 제어 대상",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(projectStatusLabel(project.status), style = MaterialTheme.typography.bodySmall)
                Text("세션 열기", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun projectStatusLabel(status: ProjectStatus): String = when (status) {
    ProjectStatus.RUNNING -> "실행 중"
    ProjectStatus.STOPPED -> "중지됨"
    ProjectStatus.STARTING -> "시작 중"
    ProjectStatus.STOPPING -> "중지 중"
    ProjectStatus.IDLE -> "유휴"
    ProjectStatus.DEGRADED -> "주의 필요"
    ProjectStatus.ERROR -> "오류"
}
