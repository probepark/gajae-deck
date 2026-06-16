package io.devnogari.gajaedeck.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Tight, consistent corner radii. Deliberately avoids the fully-rounded "pill" look of stock Material
 * components — panels and chips share a calm 8–12dp family.
 */
val GajaeDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
