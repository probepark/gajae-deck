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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.bridge.BridgeFrame
import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.command_palette
import io.devnogari.gajaedeck.resources.error_label
import io.devnogari.gajaedeck.resources.nav_pairings
import io.devnogari.gajaedeck.resources.prompt_label
import io.devnogari.gajaedeck.resources.send
import io.devnogari.gajaedeck.resources.send_log
import io.devnogari.gajaedeck.resources.session_title
import org.jetbrains.compose.resources.stringResource

private val paletteCommands = listOf("get_session_stats", "get_messages", "compact", "abort")

/** Live session screen: connection state, streamed frames, prompt input, command palette, send log. */
@Composable
fun SessionScreen(controller: SessionController, onBack: () -> Unit) {
    val state by controller.state.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.nav_pairings)) }
            Spacer(Modifier.width(8.dp))
            Text("${stringResource(Res.string.session_title)} — ${state.connection}", style = MaterialTheme.typography.titleMedium)
        }
        state.error?.let { Text("${stringResource(Res.string.error_label)}: $it", color = MaterialTheme.colorScheme.error) }

        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.frames) { frame -> FrameCard(frame) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(prompt, { prompt = it }, label = { Text(stringResource(Res.string.prompt_label)) }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { controller.sendPrompt(prompt); prompt = "" }) { Text(stringResource(Res.string.send)) }
        }

        Text(stringResource(Res.string.command_palette), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            paletteCommands.forEach { cmd -> Button(onClick = { controller.sendCommand(cmd) }) { Text(cmd) } }
        }

        Text(stringResource(Res.string.send_log), style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
            state.sentLog.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun FrameCard(frame: BridgeFrame) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "#${frame.seq ?: "-"}  ${frame.type.wire}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(frame.raw.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
