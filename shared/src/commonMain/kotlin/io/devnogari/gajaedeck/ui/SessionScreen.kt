package io.devnogari.gajaedeck.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.TranscriptItem
import io.devnogari.gajaedeck.bridge.ConnectionState
import io.devnogari.gajaedeck.bridge.MessageItem
import io.devnogari.gajaedeck.bridge.ToolCallItem
import io.devnogari.gajaedeck.bridge.GateItem
import io.devnogari.gajaedeck.bridge.NoticeItem
import io.devnogari.gajaedeck.bridge.CommandRegistry
import io.devnogari.gajaedeck.bridge.CommandMetadata
import io.devnogari.gajaedeck.bridge.FieldKind
import kotlinx.serialization.json.JsonObject
import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.command_palette
import io.devnogari.gajaedeck.resources.error_label
import io.devnogari.gajaedeck.resources.nav_pairings
import io.devnogari.gajaedeck.resources.prompt_label
import io.devnogari.gajaedeck.resources.send
import io.devnogari.gajaedeck.resources.send_log
import io.devnogari.gajaedeck.resources.session_title
import org.jetbrains.compose.resources.stringResource



/** Live session screen: connection state, streamed frames, prompt input, command palette, send log. */
@Composable
fun SessionScreen(controller: SessionController, onBack: () -> Unit) {
    val state by controller.state.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.nav_pairings)) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.session_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            ConnectionStatus(state.connection)
        }
        state.error?.let { Text("${stringResource(Res.string.error_label)}: $it", color = MaterialTheme.colorScheme.error) }

        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.transcript, key = { it.key }) { item -> TranscriptCard(item) }
        }
        if (state.gateRequests.isNotEmpty()) {
            val fieldValues = remember { mutableStateMapOf<String, String>() }
            state.gateRequests.forEach { req ->
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


        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(prompt, { prompt = it }, label = { Text(stringResource(Res.string.prompt_label)) }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { controller.sendPrompt(prompt); prompt = "" }, shape = MaterialTheme.shapes.small) { Text(stringResource(Res.string.send)) }
        }

        CommandPalette(state) { type, params -> controller.sendCommand(type, params) }

        Text(stringResource(Res.string.send_log), style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
            state.sentLog.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TranscriptCard(item: TranscriptItem) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (item) {
                is MessageItem -> {
                    Text(item.role.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(item.text, style = MaterialTheme.typography.bodySmall)
                }
                is ToolCallItem -> {
                    Text("tool: ${item.tool}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(item.summary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                is GateItem -> {
                    Text("Input needed · ${item.frameKind}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(item.preview, style = MaterialTheme.typography.bodySmall)
                }
                is NoticeItem -> {
                    Text("System · ${item.kind}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONNECTED_STREAMING -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionState.CHECKING_HEALTH -> "Connecting" to MaterialTheme.colorScheme.secondary
        ConnectionState.REPLAYING -> "Syncing" to MaterialTheme.colorScheme.secondary
        ConnectionState.BACKOFF_RECONNECTING -> "Reconnecting" to MaterialTheme.colorScheme.secondary
        ConnectionState.PAIRING -> "Pairing" to MaterialTheme.colorScheme.secondary
        ConnectionState.DISCONNECTED_BY_USER -> "Disconnected" to MaterialTheme.colorScheme.secondary
        ConnectionState.DESYNCED -> "Out of sync" to MaterialTheme.colorScheme.error
        ConnectionState.AUTH_BLOCKED, ConnectionState.TLS_BLOCKED, ConnectionState.CORS_BLOCKED,
        ConnectionState.PROTOCOL_BLOCKED, ConnectionState.ENDPOINT_DISABLED -> "Blocked" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Grouped, scope-gated command palette: the 12 exposed commands by [CommandGroup], with a schema-driven arg form. */
@Composable
private fun CommandPalette(state: SessionUiState, onSend: (String, JsonObject) -> Unit) {
    var selected by remember { mutableStateOf<CommandMetadata?>(null) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    Text(stringResource(Res.string.command_palette), style = MaterialTheme.typography.titleSmall)
    CommandRegistry.exposed.groupBy { it.group }.forEach { (group, cmds) ->
        Text(group.name.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
                ) { Text(meta.type) }
            }
        }
    }
    selected?.let { meta ->
        val requiredFilled = meta.fields.filter { it.required }.all { (fieldValues[it.name] ?: "").isNotBlank() }
        val canSend = CommandRegistry.isEnabled(meta.type, state.grantedScopes) && requiredFilled
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(meta.type, fontWeight = FontWeight.Bold)
                meta.fields.forEach { f ->
                    when (f.kind) {
                        FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label)
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = fieldValues[f.name] == "true",
                                onCheckedChange = { fieldValues[f.name] = it.toString() },
                            )
                        }
                        else -> OutlinedTextField(
                            value = fieldValues[f.name] ?: "",
                            onValueChange = { fieldValues[f.name] = it },
                            label = { Text(f.label + if (f.required) " *" else "") },
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
                    ) { Text(stringResource(Res.string.send)) }
                    TextButton(onClick = { selected = null; fieldValues.clear() }) { Text("Cancel") }
                }
            }
        }
    }
}
