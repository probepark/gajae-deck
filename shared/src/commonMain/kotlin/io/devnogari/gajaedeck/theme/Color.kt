package io.devnogari.gajaedeck.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * gajae-deck color tokens. A bespoke "developer console" palette (ink surfaces, a single mint accent,
 * borders instead of Material elevation) so the UI does not read as stock Material 3.
 */

// Accent
private val Mint = Color(0xFF4FD6BC)
private val MintDim = Color(0xFF2E8C7C)
private val MintInkText = Color(0xFF06231F)

// Dark ink scale
private val Ink900 = Color(0xFF0E1116) // app background
private val Ink800 = Color(0xFF151A21) // panels / surface
private val Ink700 = Color(0xFF1C232C) // raised / chips
private val Ink600 = Color(0xFF2A323D) // borders / outline
private val Ink500 = Color(0xFF3A4453)
private val InkText = Color(0xFFE7ECF2)
private val InkTextDim = Color(0xFF98A2B2)
private val DangerDark = Color(0xFFFF6B6B)

// Light paper scale
private val Paper0 = Color(0xFFF6F8FA) // background
private val PaperSurface = Color(0xFFFFFFFF) // surface
private val PaperVariant = Color(0xFFEDF1F5) // raised / chips
private val PaperOutline = Color(0xFFD8DEE6)
private val PaperText = Color(0xFF161B22)
private val PaperTextDim = Color(0xFF5B6675)
private val Danger = Color(0xFFC0362C)

val GajaeDeckDarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = MintInkText,
    primaryContainer = Color(0xFF123A34),
    onPrimaryContainer = Color(0xFF9CF0DD),
    secondary = InkTextDim,
    onSecondary = Ink900,
    secondaryContainer = Ink700,
    onSecondaryContainer = InkText,
    background = Ink900,
    onBackground = InkText,
    surface = Ink800,
    onSurface = InkText,
    surfaceVariant = Ink700,
    onSurfaceVariant = InkTextDim,
    surfaceContainerHighest = Ink700,
    outline = Ink600,
    outlineVariant = Ink500,
    error = DangerDark,
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFB4AB),
)

val GajaeDeckLightColors = lightColorScheme(
    primary = MintDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2E8),
    onPrimaryContainer = Color(0xFF053A32),
    secondary = PaperTextDim,
    onSecondary = Color.White,
    secondaryContainer = PaperVariant,
    onSecondaryContainer = PaperText,
    background = Paper0,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = PaperTextDim,
    surfaceContainerHighest = PaperVariant,
    outline = PaperOutline,
    outlineVariant = Color(0xFFE4E9EF),
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)
