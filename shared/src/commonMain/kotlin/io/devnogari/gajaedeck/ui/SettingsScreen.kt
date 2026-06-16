package io.devnogari.gajaedeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.nav_pairings
import io.devnogari.gajaedeck.resources.nav_settings
import io.devnogari.gajaedeck.resources.settings_theme
import io.devnogari.gajaedeck.resources.theme_dark
import io.devnogari.gajaedeck.resources.theme_light
import io.devnogari.gajaedeck.resources.theme_system
import io.devnogari.gajaedeck.resources.web_lower_assurance_notice
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.settings.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: theme mode selector (writes through AppSettings) plus a lower-assurance notice shown only
 * when the platform secure store is not OS-backed (web localStorage).
 */
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    storageLowerAssurance: Boolean,
    onBack: () -> Unit,
) {
    val themeMode by appSettings.themeMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.nav_pairings)) }
            Text(stringResource(Res.string.nav_settings), style = MaterialTheme.typography.headlineSmall)
        }

        Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip(ThemeMode.SYSTEM, Res.string.theme_system, themeMode, appSettings::setThemeMode)
            ThemeChip(ThemeMode.LIGHT, Res.string.theme_light, themeMode, appSettings::setThemeMode)
            ThemeChip(ThemeMode.DARK, Res.string.theme_dark, themeMode, appSettings::setThemeMode)
        }

        if (storageLowerAssurance) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.web_lower_assurance_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    mode: ThemeMode,
    label: StringResource,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(label)) },
    )
}
