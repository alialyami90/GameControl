package com.gamecontroller.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.gamecontroller.model.Accent

/**
 * Recovery Rig palette: graphite hard-case grounds, bone text, and hi-vis signal colours that
 * only appear when something is on, armed, or wrong.
 */
object Rig {
    val Chassis = Color(0xFF101312)
    val Case = Color(0xFF171B19)
    val CaseRaised = Color(0xFF1F2421)
    val Cap = Color(0xFF272D2A)
    val CapHigh = Color(0xFF323935)
    val CapPressed = Color(0xFF1B1F1D)
    val Stitch = Color(0xFF39423E)
    val StitchStrong = Color(0xFF55605A)

    val Bone = Color(0xFFECE9DF)
    val BoneMuted = Color(0xFFA9B0A9)
    val BoneFaint = Color(0xFF7E8780)

    val HiVis = Color(0xFFFF6A13)
    val OnHiVis = Color(0xFF1C0B00)
    val Amber = Color(0xFFFFB21E)
    val Yellow = Color(0xFFF2C200)
    val Green = Color(0xFF4CD27A)
    val Red = Color(0xFFFF4D3F)
    val Blue = Color(0xFF5AA8FF)
    val Cyan = Color(0xFF3ED6DF)
}

/** Colour a control lights up in. Neutral controls use the house hi-vis orange. */
fun Accent.lit(): Color = when (this) {
    Accent.NEUTRAL -> Rig.HiVis
    Accent.AMBER -> Rig.Amber
    Accent.GREEN -> Rig.Green
    Accent.RED -> Rig.Red
    Accent.BLUE -> Rig.Blue
    Accent.CYAN -> Rig.Cyan
}

fun Accent.displayName(): String = when (this) {
    Accent.NEUTRAL -> "Hi-vis"
    Accent.AMBER -> "Amber"
    Accent.GREEN -> "Green"
    Accent.RED -> "Red"
    Accent.BLUE -> "Blue"
    Accent.CYAN -> "Cyan"
}

/** Ink to put on a filled accent surface. All accents are light enough for dark ink. */
val onAccent: Color = Color(0xFF14110C)

@Immutable
data class RigColors(
    val chassis: Color = Rig.Chassis,
    val case: Color = Rig.Case,
    val caseRaised: Color = Rig.CaseRaised,
    val cap: Color = Rig.Cap,
    val capHigh: Color = Rig.CapHigh,
    val capPressed: Color = Rig.CapPressed,
    val stitch: Color = Rig.Stitch,
    val stitchStrong: Color = Rig.StitchStrong,
    val text: Color = Rig.Bone,
    val textMuted: Color = Rig.BoneMuted,
    val textFaint: Color = Rig.BoneFaint,
    val active: Color = Rig.HiVis,
    val attention: Color = Rig.Yellow,
    val ok: Color = Rig.Green,
    val critical: Color = Rig.Red,
)
