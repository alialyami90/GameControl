package com.gamecontroller.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark by use, not by habit: the phone sits beside a monitor in a dim room and is read from
 * arm's length, so the ground stays near-black and only live controls carry colour.
 */
private val RaceScheme = darkColorScheme(
    primary = Race.Electric,
    onPrimary = Race.OnElectric,
    primaryContainer = Color(0xFF4A0F2A),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Race.Cyan,
    onSecondary = Color(0xFF002020),
    secondaryContainer = Color(0xFF1E2C3A),
    onSecondaryContainer = Color(0xFFD3E4F5),
    tertiary = Race.Green,
    onTertiary = Color(0xFF00210E),
    tertiaryContainer = Color(0xFF0F3A22),
    onTertiaryContainer = Color(0xFFB8F5CD),
    error = Race.Red,
    onError = Color(0xFF2A0500),
    errorContainer = Color(0xFF4A1610),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Race.Ground,
    onBackground = Race.Ink,
    surface = Race.Ground,
    onSurface = Race.Ink,
    surfaceVariant = Race.Cap,
    onSurfaceVariant = Race.InkMuted,
    surfaceDim = Race.Ground,
    surfaceBright = Race.CapHigh,
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Race.Panel,
    surfaceContainer = Race.PanelRaised,
    surfaceContainerHigh = Race.Cap,
    surfaceContainerHighest = Race.CapHigh,
    outline = Race.LineStrong,
    outlineVariant = Race.Line,
    inverseSurface = Race.Ink,
    inverseOnSurface = Race.Ground,
    inversePrimary = Color(0xFFB0004F),
    scrim = Color(0xFF000000),
)

val RaceShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun GcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RaceScheme,
        typography = RaceTypography,
        shapes = RaceShapes,
        content = content,
    )
}
