package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.auth.StoredPairing
import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.app_name
import io.devnogari.gajaedeck.resources.connect_failed
import io.devnogari.gajaedeck.resources.field_host
import io.devnogari.gajaedeck.resources.field_port
import io.devnogari.gajaedeck.resources.field_token
import io.devnogari.gajaedeck.resources.connect
import io.devnogari.gajaedeck.resources.nav_pairings
import io.devnogari.gajaedeck.resources.nav_settings
import io.devnogari.gajaedeck.resources.pairings_empty
import io.devnogari.gajaedeck.settings.PairingMetadata
import org.jetbrains.compose.resources.stringResource

/**
 * Entry screen: saved pairings (tap to open a session) plus a manual connect form. Connecting hands a
 * fully-formed [StoredPairing] to the caller, which persists the secret via PairingRepository (the
 * token never travels through the navigation backstack).
 */
@Composable
fun PairingsListScreen(
    pairings: List<PairingMetadata>,
    onConnect: (StoredPairing) -> Unit,
    onOpenPairing: (String) -> Unit,
    onOpenSettings: () -> Unit,
    connectFailed: Boolean = false,
) {
    var host by remember { mutableStateOf("100.x.y.z") }
    var port by remember { mutableStateOf("4077") }
    var token by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.nav_settings)) }
        }

        Text(stringResource(Res.string.nav_pairings), style = MaterialTheme.typography.titleMedium)
        if (pairings.isEmpty()) {
            Text(stringResource(Res.string.pairings_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(pairings) { pairing ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(pairing.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("${pairing.host}:${pairing.port}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onOpenPairing(pairing.id) }) { Text(stringResource(Res.string.connect)) }
                        }
                    }
                }
            }
        }

        OutlinedTextField(host, { host = it }, label = { Text(stringResource(Res.string.field_host)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it }, label = { Text(stringResource(Res.string.field_port)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text(stringResource(Res.string.field_token)) }, modifier = Modifier.fillMaxWidth())
        if (connectFailed) {
            Text(stringResource(Res.string.connect_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                onConnect(
                    StoredPairing(
                        id = "$host:$port",
                        displayName = "$host:$port",
                        baseUrl = "https://$host:$port",
                        host = host,
                        port = port.toIntOrNull() ?: 4077,
                        token = token,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.connect))
        }
    }
}
