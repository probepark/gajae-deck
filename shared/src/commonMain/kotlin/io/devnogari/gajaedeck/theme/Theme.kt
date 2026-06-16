package io.devnogari.gajaedeck.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.devnogari.gajaedeck.settings.ThemeMode

/** Resolve a [ThemeMode] to a concrete dark/light decision for the current platform. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * App-wide Material 3 theme. Applies the gajae-deck color scheme (resolved from [themeMode]) and the
 * Noto Sans KR typography. Theme state itself is owned by AppSettings; this composable only renders it.
 */
@Composable
fun GajaeDeckTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark()) GajaeDeckDarkColors else GajaeDeckLightColors,
        typography = gajaeDeckTypography(),
        shapes = GajaeDeckShapes,
        content = content,
    )
}
