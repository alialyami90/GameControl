package com.gamecontroller.ui.theme

import androidx.compose.ui.graphics.Color
import com.gamecontroller.model.Accent

/**
 * Race Front-End palette: blue-black menu grounds, cool white ink, and one electric magenta
 * that marks whatever is selected, on, or live. Signal colours appear only where they mean
 * something (green confirmed, amber waiting, red critical).
 */
object Race {
    val Ground = Color(0xFF0A0D14)
    val Panel = Color(0xFF111622)
    val PanelRaised = Color(0xFF171D2B)
    val Cap = Color(0xFF1C2333)
    val CapHigh = Color(0xFF262F44)
    val CapPressed = Color(0xFF141A27)
    val Line = Color(0xFF2B3448)
    val LineStrong = Color(0xFF465170)

    val Ink = Color(0xFFF2F4F8)
    val InkMuted = Color(0xFFA0A9BC)
    val InkFaint = Color(0xFF7C869C)

    val Electric = Color(0xFFFF2E7E)
    val OnElectric = Color(0xFF1F0010)
    val Amber = Color(0xFFFFB020)
    val Yellow = Color(0xFFFFD43B)
    val Green = Color(0xFF34E07F)
    val Red = Color(0xFFFF4B3A)
    val Blue = Color(0xFF4C9BFF)
    val Cyan = Color(0xFF2EE6E6)
}

/** Colour a control lights up in. Neutral controls use the house electric magenta. */
fun Accent.lit(): Color = when (this) {
    Accent.NEUTRAL -> Race.Electric
    Accent.AMBER -> Race.Amber
    Accent.GREEN -> Race.Green
    Accent.RED -> Race.Red
    Accent.BLUE -> Race.Blue
    Accent.CYAN -> Race.Cyan
}

fun Accent.displayName(): String = when (this) {
    Accent.NEUTRAL -> "Electric"
    Accent.AMBER -> "Amber"
    Accent.GREEN -> "Green"
    Accent.RED -> "Red"
    Accent.BLUE -> "Blue"
    Accent.CYAN -> "Cyan"
}

/** Ink for text on a filled accent surface. */
val OnAccent: Color = Color(0xFF0A0D14)
