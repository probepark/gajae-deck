package io.devnogari.gajaedeck.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Brand color tokens for the gajae-deck Material 3 light/dark schemes. */
private val BrandTeal = Color(0xFF1E6F6B)
private val BrandTealDark = Color(0xFF6FD8D2)
private val Danger = Color(0xFFBA1A1A)
private val DangerDark = Color(0xFFFFB4AB)

val GajaeDeckLightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    secondary = Color(0xFF4A6360),
    error = Danger,
    background = Color(0xFFFBFDFC),
    surface = Color(0xFFFBFDFC),
)

val GajaeDeckDarkColors = darkColorScheme(
    primary = BrandTealDark,
    onPrimary = Color(0xFF003735),
    secondary = Color(0xFFB1CCC8),
    error = DangerDark,
    background = Color(0xFF191C1B),
    surface = Color(0xFF191C1B),
)
