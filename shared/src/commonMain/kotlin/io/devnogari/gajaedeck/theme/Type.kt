package io.devnogari.gajaedeck.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.notosanskr
import org.jetbrains.compose.resources.Font

/**
 * Noto Sans KR (OFL) family. A single variable font supplies the limited weights we use
 * (Regular/Medium/Bold) so Korean text renders with real glyphs instead of tofu boxes, on every
 * platform. The bundled subset keeps payload small (see G007 payload check / NOTICE for OFL).
 */
@Composable
fun notoSansKrFamily(): FontFamily = FontFamily(
    Font(Res.font.notosanskr, FontWeight.Normal),
    Font(Res.font.notosanskr, FontWeight.Medium),
    Font(Res.font.notosanskr, FontWeight.Bold),
)

/** Material 3 typography with every text style bound to [notoSansKrFamily]. */
@Composable
fun gajaeDeckTypography(): Typography {
    val family = notoSansKrFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    )
}
