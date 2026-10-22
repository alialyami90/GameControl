package com.gamecontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun cond(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, italic: Boolean = false) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
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

/** Display and headline roles are italic, the racing-menu voice; body stays upright Barlow. */
val RaceTypography = Typography(
    displayLarge = cond(57, 60, FontWeight.ExtraBold, -0.01, italic = true),
    displayMedium = cond(45, 48, FontWeight.ExtraBold, -0.01, italic = true),
    displaySmall = cond(36, 40, FontWeight.ExtraBold, italic = true),
    headlineLarge = cond(32, 36, FontWeight.ExtraBold, italic = true),
    headlineMedium = cond(28, 32, FontWeight.ExtraBold, italic = true),
    headlineSmall = cond(24, 28, FontWeight.Bold, italic = true),
    titleLarge = cond(22, 26, FontWeight.Bold, 0.01),
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
object RaceType {
    /** Label printed on a control cap. Uppercase in use. */
    val capLabel = cond(14, 16, FontWeight.SemiBold, 0.07)
    val capLabelLarge = cond(17, 20, FontWeight.SemiBold, 0.06)
    /** Italic tabular numerals for gear, speed and slider values. */
    val readout = cond(20, 22, FontWeight.Bold, italic = true).copy(fontFeatureSettings = "tnum")
    val readoutLarge = cond(34, 34, FontWeight.ExtraBold, italic = true).copy(fontFeatureSettings = "tnum")
    val readoutHuge = cond(52, 52, FontWeight.ExtraBold, -0.01, italic = true).copy(fontFeatureSettings = "tnum")
    val unit = cond(11, 12, FontWeight.SemiBold, 0.1)
    /** Profile and item names in the italic title voice. */
    val title = cond(20, 24, FontWeight.Bold, italic = true)
    /** Screen title plate: italic, uppercase in use. */
    val plate = cond(26, 28, FontWeight.ExtraBold, 0.01, italic = true)
}
