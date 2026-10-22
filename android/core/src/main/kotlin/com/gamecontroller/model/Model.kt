package com.gamecontroller.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which game a profile targets. Drives built-in presets and the BeamNG integration. */
@Serializable
enum class GameId(val displayName: String) {
    @SerialName("beamng") BEAMNG("BeamNG.drive"),
    @SerialName("mudrunner") MUDRUNNER("MudRunner"),
    @SerialName("custom") CUSTOM("Custom"),
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val game: GameId = GameId.CUSTOM,
    val builtIn: Boolean = false,
    val pages: List<Page> = listOf(Page(id = "p1", name = "Main")),
    /** How this profile's inputs reach Windows. */
    val output: OutputRoute = OutputRoute.AUTO,
    /** Short description shown on the profile card. */
    val description: String = "",
    /** Schema version of the exported JSON, not the network protocol version. */
    val schema: Int = 1,
)

/** Where a profile's inputs go. Game commands (BeamNG) and XInput always need the companion. */
@Serializable
enum class OutputRoute {
    /** Bluetooth HID when the phone is paired as "GameCtl Pad", otherwise the Wi-Fi companion. */
    @SerialName("auto") AUTO,
    /** Bluetooth HID only (no PC software). */
    @SerialName("hid") HID,
    /** Wi-Fi companion only (scancodes, vJoy, ViGEm). */
    @SerialName("companion") COMPANION,
}

/** A page is a grid. Controls are placed on cells and may span several. */
@Serializable
data class Page(
    val id: String,
    val name: String,
    val columns: Int = 8,
    val rows: Int = 4,
    val controls: List<Control> = emptyList(),
)

@Serializable
enum class ControlType {
    @SerialName("momentary") MOMENTARY,
    @SerialName("latching") LATCHING,
    @SerialName("switch") SWITCH,
    @SerialName("radio") RADIO,
    @SerialName("slider") SLIDER,
    @SerialName("rotary") ROTARY;

    val wire: String get() = name.lowercase()
}

/** Accent colour role for a control. The UI theme maps each role to a colour. */
@Serializable
enum class Accent {
    @SerialName("neutral") NEUTRAL,
    @SerialName("amber") AMBER,
    @SerialName("green") GREEN,
    @SerialName("red") RED,
    @SerialName("blue") BLUE,
    @SerialName("cyan") CYAN,
}

@Serializable
data class Control(
    val id: String,
    val type: ControlType,
    val label: String,
    /** Id from the icon library (see IconIds), or null for a text-only control. */
    val icon: String? = null,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    val accent: Accent = Accent.NEUTRAL,
    /** Used by MOMENTARY, LATCHING and SLIDER. */
    val action: Action = Action.None,
    /** Used by SWITCH and RADIO: one entry per position, in order. */
    val positions: List<Position> = emptyList(),
    /** Used by ROTARY. */
    val cw: Action = Action.None,
    val ccw: Action = Action.None,
    /** SLIDER: return to the rest value when released. */
    val springReturn: Boolean = false,
    /** SLIDER: BIPOLAR sends -1..1 and rests at 0, UNIPOLAR sends 0..1 and rests at 0. */
    val range: AxisRange = AxisRange.UNIPOLAR,
    /** SLIDER: true when the slider is drawn vertically. */
    val vertical: Boolean = true,
    /** Game state key that lights the control's indicator, e.g. "lights.low". Null means local latch state. */
    val feedbackKey: String? = null,
    /** Game state availability key, e.g. "has.diff.front". The control is dimmed and disabled when it is false. */
    val visibleWhen: String? = null,
    /** SWITCH/RADIO: game state key whose string or number value selects the active position. */
    val positionKey: String? = null,
    /**
     * Game input action this control's gamepad button should be bound to inside the game,
     * e.g. BeamNG "toggle_headlights". Used by "Bind in game" (BeamNG mod bindings.apply).
     */
    val bindTo: String? = null,
)

@Serializable
data class Position(
    val label: String,
    val action: Action = Action.None,
    val icon: String? = null,
    /** Value of positionKey that means this position is active, e.g. "D". */
    val stateValue: String? = null,
)

@Serializable
enum class AxisRange {
    @SerialName("bipolar") BIPOLAR,
    @SerialName("unipolar") UNIPOLAR,
}

@Serializable
enum class KeyTrigger {
    @SerialName("hold") HOLD,
    @SerialName("tap") TAP,
    @SerialName("press") PRESS,
}

@Serializable
data class MacroStep(
    val keys: List<String> = emptyList(),
    val holdMs: Int = 0,
    val delayMs: Int = 0,
)

/** What a control does. Serialized exactly as the protocol's Action object. */
@Serializable
sealed interface Action {
    @Serializable @SerialName("none")
    data object None : Action

    @Serializable @SerialName("key")
    data class Key(val keys: List<String>, val trigger: KeyTrigger = KeyTrigger.HOLD) : Action

    @Serializable @SerialName("vjoy_button")
    data class VJoyButton(val device: Int = 1, val button: Int) : Action

    @Serializable @SerialName("vjoy_axis")
    data class VJoyAxis(val device: Int = 1, val axis: String, val range: AxisRange = AxisRange.BIPOLAR) : Action

    @Serializable @SerialName("macro")
    data class Macro(val steps: List<MacroStep>) : Action

    @Serializable @SerialName("beamng")
    data class BeamNG(val command: String) : Action

    @Serializable @SerialName("xinput_button")
    data class XInputButton(val button: String) : Action

    @Serializable @SerialName("xinput_axis")
    data class XInputAxis(val axis: String) : Action
}
