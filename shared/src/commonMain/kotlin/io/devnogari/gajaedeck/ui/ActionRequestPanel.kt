package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = request.summary,
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
                        Button(
                            onClick = { onAction(action.id) },
                            enabled = ActionRequestPanelLogic.actionsEnabled(request) &&
                                (action.id != "submit" || ActionRequestPanelLogic.canSubmit(request, fieldValues)),
                            colors = if (action.style == GateActionStyle.DESTRUCTIVE) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(text = action.label)
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
            label = { Text(text = field.label) },
            placeholder = field.placeholder?.let { placeholder -> ({ Text(text = placeholder) }) },
            singleLine = field.kind != FieldKind.MULTILINE && field.kind != FieldKind.JSON,
            isError = !ActionRequestPanelLogic.fieldValid(field, value),
        )

        FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = field.label, modifier = Modifier.weight(1f))
            Switch(
                checked = value == "true",
                onCheckedChange = { onFieldChange(field.name, it.toString()) },
            )
        }

        FieldKind.ENUM -> Column {
            Text(text = field.label, style = MaterialTheme.typography.bodySmall)
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
