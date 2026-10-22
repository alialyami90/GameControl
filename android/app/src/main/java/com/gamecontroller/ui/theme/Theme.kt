package com.gamecontroller.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app is dark by design rather than by default: it is used in a room lit by the game
 * monitor, glanced at from arm's length, so only lit controls should glow.
 */
private val RigScheme = darkColorScheme(
    primary = Rig.HiVis,
    onPrimary = Rig.OnHiVis,
    primaryContainer = Color(0xFF4A2410),
    onPrimaryContainer = Color(0xFFFFD9C4),
    secondary = Rig.Yellow,
    onSecondary = Color(0xFF1E1800),
    secondaryContainer = Color(0xFF3A3420),
    onSecondaryContainer = Color(0xFFF6E7B0),
    tertiary = Rig.Green,
    onTertiary = Color(0xFF00210C),
    tertiaryContainer = Color(0xFF173A24),
    onTertiaryContainer = Color(0xFFBDF2CD),
    error = Rig.Red,
    onError = Color(0xFF2A0300),
    errorContainer = Color(0xFF4A1410),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Rig.Chassis,
    onBackground = Rig.Bone,
    surface = Rig.Chassis,
    onSurface = Rig.Bone,
    surfaceVariant = Rig.Cap,
    onSurfaceVariant = Rig.BoneMuted,
    surfaceDim = Rig.Chassis,
    surfaceBright = Rig.CapHigh,
    surfaceContainerLowest = Color(0xFF0C0E0D),
    surfaceContainerLow = Rig.Case,
    surfaceContainer = Rig.CaseRaised,
    surfaceContainerHigh = Rig.Cap,
    surfaceContainerHighest = Rig.CapHigh,
    outline = Rig.StitchStrong,
    outlineVariant = Rig.Stitch,
    inverseSurface = Rig.Bone,
    inverseOnSurface = Rig.Chassis,
    inversePrimary = Color(0xFF9C3F00),
    scrim = Color(0xFF000000),
)

val RigShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val LocalRigColors = staticCompositionLocalOf { RigColors() }

object RigTheme {
    val colors: RigColors
        @Composable @ReadOnlyComposable get() = LocalRigColors.current
}

@Composable
fun GcTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRigColors provides RigColors()) {
        MaterialTheme(
            colorScheme = RigScheme,
            typography = RigTypography,
            shapes = RigShapes,
            content = content,
        )
    }
}
