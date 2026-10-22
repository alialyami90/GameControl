package com.gamecontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun cond(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val RigTypography = Typography(
    displayLarge = cond(56, 60, FontWeight.Bold, -0.01),
    displayMedium = cond(44, 48, FontWeight.Bold, -0.01),
    displaySmall = cond(36, 40, FontWeight.Bold),
    headlineLarge = cond(32, 36, FontWeight.Bold),
    headlineMedium = cond(28, 32, FontWeight.Bold),
    headlineSmall = cond(24, 28, FontWeight.SemiBold),
    titleLarge = cond(22, 26, FontWeight.SemiBold, 0.01),
    titleMedium = cond(18, 22, FontWeight.SemiBold, 0.02),
    titleSmall = cond(15, 20, FontWeight.SemiBold, 0.03),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 20),
    bodySmall = body(12, 16),
    labelLarge = cond(15, 18, FontWeight.SemiBold, 0.06),
    labelMedium = cond(13, 16, FontWeight.SemiBold, 0.07),
    labelSmall = cond(11, 14, FontWeight.SemiBold, 0.08),
)

/** Styles the Material scale has no role for: control caps and HUD numerals. */
object RigType {
    /** Label printed on a control cap. Uppercase in use. */
    val capLabel = cond(14, 16, FontWeight.SemiBold, 0.07)
    val capLabelLarge = cond(17, 20, FontWeight.SemiBold, 0.06)
    /** Tabular numerals for gear, speed, slider values. */
    val readout = cond(20, 22, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
    val readoutLarge = cond(34, 34, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
    val readoutHuge = cond(48, 48, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
    val unit = cond(11, 12, FontWeight.SemiBold, 0.1)
}
