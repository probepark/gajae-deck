package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.FieldDescriptor
import io.devnogari.gajaedeck.bridge.FieldKind
import io.devnogari.gajaedeck.bridge.GateActionStyle
import io.devnogari.gajaedeck.bridge.GateRequest

@Composable
fun ActionRequestPanel(
    request: GateRequest,
    fieldValues: Map<String, String>,
    onFieldChange: (String, String) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = requestSummary(request.summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (request.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = request.details, style = MaterialTheme.typography.bodySmall)
            }

            request.fields.forEach { field ->
                Spacer(modifier = Modifier.height(12.dp))
                ActionRequestField(
                    field = field,
                    value = fieldValues[field.name] ?: "",
                    onFieldChange = onFieldChange,
                )
            }

            request.failClosedReason?.let { reason ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = reason, color = MaterialTheme.colorScheme.error)
            }

            if (request.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    request.actions.forEachIndexed { index, action ->
                        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                        val actionEnabled = ActionRequestPanelLogic.actionsEnabled(request) &&
                            (action.id != "submit" || ActionRequestPanelLogic.canSubmit(request, fieldValues))
                        when (action.style) {
                            GateActionStyle.DESTRUCTIVE -> OutlinedButton(
                                onClick = { onAction(action.id) },
                                enabled = actionEnabled,
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) {
                                Text(actionLabel(action.id, action.label))
                            }

                            GateActionStyle.PRIMARY -> Button(
                                onClick = { onAction(action.id) },
                                enabled = actionEnabled,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(actionLabel(action.id, action.label))
                            }

                            GateActionStyle.NEUTRAL -> FilledTonalButton(
                                onClick = { onAction(action.id) },
                                enabled = actionEnabled,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(actionLabel(action.id, action.label))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRequestField(
    field: FieldDescriptor,
    value: String,
    onFieldChange: (String, String) -> Unit,
) {
    when (field.kind) {
        FieldKind.TEXT,
        FieldKind.NUMBER,
        FieldKind.JSON,
        FieldKind.MULTILINE,
        -> OutlinedTextField(
            value = value,
            onValueChange = { onFieldChange(field.name, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = fieldLabel(field)) },
            placeholder = field.placeholder?.let { { Text(text = it) } },
            singleLine = field.kind != FieldKind.MULTILINE && field.kind != FieldKind.JSON,
            isError = !ActionRequestPanelLogic.fieldValid(field, value),
        )

        FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = fieldLabel(field), modifier = Modifier.weight(1f))
            Switch(
                checked = value == "true",
                onCheckedChange = { onFieldChange(field.name, it.toString()) },
            )
        }

        FieldKind.ENUM -> Column {
            Text(text = fieldLabel(field), style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                field.options.forEachIndexed { index, option ->
                    if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onFieldChange(field.name, option) }) {
                        Text(
                            text = option,
                            fontWeight = if (value == option) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun requestSummary(summary: String): String = when {
    summary.startsWith("Permission: ") -> "권한 승인 · ${summary.removePrefix("Permission: ")}"
    summary.startsWith("Workflow: ") -> "워크플로 확인 · ${summary.removePrefix("Workflow: ")}"
    summary.startsWith("Input: ") -> "추가 입력 · ${summary.removePrefix("Input: ")}"
    else -> summary
}

private fun actionLabel(id: String, fallback: String): String = when (id) {
    "allow" -> "허용"
    "deny" -> "거부"
    "always" -> "항상 허용"
    "continue" -> "계속"
    "cancel" -> "취소"
    "submit" -> "제출"
    "approve" -> "승인"
    else -> fallback
}

private fun fieldLabel(field: FieldDescriptor): String = when (field.label) {
    "Message" -> "메시지"
    "Session ID" -> "세션 ID"
    "Name" -> "이름"
    "Reason" -> "사유"
    else -> field.label
}
