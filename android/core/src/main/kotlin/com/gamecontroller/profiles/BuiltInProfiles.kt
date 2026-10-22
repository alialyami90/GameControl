package com.gamecontroller.profiles

import com.gamecontroller.model.Accent
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.GameId
import com.gamecontroller.model.KeyTrigger
import com.gamecontroller.model.OutputRoute
import com.gamecontroller.model.Page
import com.gamecontroller.model.Position
import com.gamecontroller.model.Profile

/**
 * Profiles that ship with the app. Icon ids come from ui/icons/IconLibrary.kt; state keys and
 * BeamNG command names come from protocol/PROTOCOL.md.
 */
object BuiltInProfiles {
    const val GAMEPAD_ID = "builtin-gamepad"
    const val KEYBOARD_ID = "builtin-keyboard"
    const val BEAMNG_ID = "builtin-beamng"
    const val MUDRUNNER_ID = "builtin-mudrunner"

    val all: List<Profile> by lazy { listOf(gamepad(), keyboard(), beamng(), mudrunner()) }

    // ---- small builders --------------------------------------------------------------------

    private fun bng(cmd: String) = Action.BeamNG(cmd)
    private fun key(vararg k: String, trigger: KeyTrigger = KeyTrigger.HOLD) = Action.Key(k.toList(), trigger)
    private fun btn(n: Int) = Action.VJoyButton(1, n)

    private fun momentary(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent = Accent.NEUTRAL, w: Int = 1, h: Int = 1, feedback: String? = null, visibleWhen: String? = null) =
        Control(id, ControlType.MOMENTARY, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    private fun latching(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent, feedback: String? = null, visibleWhen: String? = null, w: Int = 1, h: Int = 1) =
        Control(id, ControlType.LATCHING, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    // ---- Generic gamepad: works with any game over Bluetooth HID or vJoy -------------------

    fun gamepad(): Profile {
        val buttons = (1..12).map { n ->
            val col = (n - 1) % 6
            val row = (n - 1) / 6
            momentary("gp_b$n", "B$n", null, 2 + col, row, btn(n), if (n <= 4) Accent.CYAN else Accent.NEUTRAL)
        }
        val toggles = (13..16).map { n ->
            latching("gp_t$n", "T${n - 12}", "power", 2 + (n - 13), 2, btn(n), Accent.AMBER)
        }
        val main = Page(
            id = "gp_main", name = "Pad", columns = 8, rows = 4,
            controls = buttons + toggles + listOf(
                Control("gp_throttle", ControlType.SLIDER, "Throttle", "speed", 0, 0, 1, 4, Accent.GREEN,
                    Action.VJoyAxis(1, "SL0", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR),
                Control("gp_axis_z", ControlType.SLIDER, "Z axis", null, 1, 0, 1, 4, Accent.CYAN,
                    Action.VJoyAxis(1, "Z"), range = AxisRange.BIPOLAR, springReturn = true),
                Control("gp_mode", ControlType.SWITCH, "Mode", "settings", 6, 2, 2, 1, Accent.AMBER,
                    positions = listOf(Position("1", btn(17)), Position("2", btn(18)), Position("3", btn(19)))),
                Control("gp_dial", ControlType.ROTARY, "Dial", null, 2 + 4, 3, 2, 1, Accent.NEUTRAL,
                    cw = btn(20), ccw = btn(21)),
                momentary("gp_b22", "B22", null, 2, 3, btn(22)),
                momentary("gp_b23", "B23", null, 3, 3, btn(23)),
                momentary("gp_b24", "B24", null, 4, 3, btn(24)),
                momentary("gp_b25", "B25", null, 5, 3, btn(25)),
            ),
        )
        return Profile(
            id = GAMEPAD_ID, name = "Generic gamepad", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(main), output = OutputRoute.AUTO,
            description = "Gamepad buttons, toggles, a throttle and an axis for any game. Bind them in the game's controls menu.",
        )
    }

    // ---- Generic keyboard macro pad -------------------------------------------------------

    fun keyboard(): Profile {
        val fkeys = (1..12).map { n ->
            momentary("kb_f$n", "F$n", null, (n - 1) % 6, (n - 1) / 6, key("F$n"), Accent.NEUTRAL)
        }
        val page = Page(
            id = "kb_main", name = "Keys", columns = 8, rows = 4,
            controls = fkeys + listOf(
                momentary("kb_esc", "Esc", "menu", 6, 0, key("ESC"), Accent.RED),
                momentary("kb_enter", "Enter", null, 7, 0, key("ENTER"), Accent.GREEN),
                momentary("kb_tab", "Tab", null, 6, 1, key("TAB")),
                momentary("kb_space", "Space", null, 7, 1, key("SPACE")),
                momentary("kb_up", "Up", "arrow_up", 1, 2, key("UP")),
                momentary("kb_left", "Left", "arrow_left", 0, 3, key("LEFT")),
                momentary("kb_down", "Down", "arrow_down", 1, 3, key("DOWN")),
                momentary("kb_right", "Right", "arrow_right", 2, 3, key("RIGHT")),
                momentary("kb_screenshot", "Screenshot", "screenshot", 4, 2, key("PRINTSCREEN", trigger = KeyTrigger.PRESS)),
                momentary("kb_mute", "Push to talk", "mic", 5, 2, key("CAPSLOCK"), Accent.AMBER),
                Control("kb_volume", ControlType.ROTARY, "Volume", "volume_up", 4, 3, 2, 1, Accent.CYAN,
                    cw = key("EQUALS", trigger = KeyTrigger.PRESS), ccw = key("MINUS", trigger = KeyTrigger.PRESS)),
                momentary("kb_copy", "Copy", null, 6, 2, key("LCTRL", "C", trigger = KeyTrigger.PRESS)),
                momentary("kb_paste", "Paste", null, 7, 2, key("LCTRL", "V", trigger = KeyTrigger.PRESS)),
                momentary("kb_quicksave", "Quick save", "star", 6, 3, key("F5", trigger = KeyTrigger.PRESS), Accent.GREEN),
                momentary("kb_quickload", "Quick load", "replay", 7, 3, key("F9", trigger = KeyTrigger.PRESS), Accent.AMBER),
            ),
        )
        return Profile(
            id = KEYBOARD_ID, name = "Keyboard keys", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(page), output = OutputRoute.AUTO,
            description = "Function keys, arrows and common shortcuts. Works in any game or app.",
        )
    }

    // ---- BeamNG.drive: game commands through the mod, with feedback ----------------------

    private val GEARBOX_MODES = listOf("P", "R", "N", "D", "S", "M")

    fun beamng(): Profile {
        val drive = Page(
            id = "bng_drive", name = "Drive", columns = 8, rows = 4,
            controls = listOf(
                momentary("bng_ignition", "Ignition", "ignition", 0, 0, bng("ignition.toggle"), Accent.AMBER, feedback = "ignition"),
                momentary("bng_starter", "Start", "starter", 1, 0, bng("starter"), Accent.RED, feedback = "engine.running"),
                momentary("bng_horn", "Horn", "horn", 2, 0, bng("horn"), Accent.NEUTRAL),
                latching("bng_hazard", "Hazard", "hazard", 3, 0, bng("hazard"), Accent.RED, feedback = "hazard"),
                latching("bng_sig_l", "Left", "signal_left", 4, 0, bng("signal.left"), Accent.GREEN, feedback = "signal.left"),
                latching("bng_sig_r", "Right", "signal_right", 5, 0, bng("signal.right"), Accent.GREEN, feedback = "signal.right"),
                latching("bng_pbrake", "Park brake", "parking_brake", 6, 0, bng("parkingbrake"), Accent.RED, feedback = "parkingbrake", w = 2),

                latching("bng_low", "Low beam", "low_beam", 0, 1, bng("lights.low"), Accent.GREEN, feedback = "lights.low"),
                latching("bng_high", "High beam", "high_beam", 1, 1, bng("lights.high"), Accent.BLUE, feedback = "lights.high"),
                latching("bng_fog", "Fog", "fog_light", 2, 1, bng("lights.fog"), Accent.GREEN, feedback = "lights.fog", visibleWhen = "has.fog"),
                latching("bng_lightbar", "Light bar", "light_bar", 3, 1, bng("lights.light_bar"), Accent.AMBER, feedback = "lights.light_bar", visibleWhen = "has.light_bar"),
                latching("bng_esc", "ESC / TC", "esc", 4, 1, bng("esc"), Accent.AMBER, feedback = "esc.active", visibleWhen = "has.esc"),
                latching("bng_cruise", "Cruise", "cruise", 5, 1, bng("cruise"), Accent.GREEN, feedback = "cruise.active", visibleWhen = "has.cruise"),
                momentary("bng_cruise_set", "Set", "speed", 6, 1, bng("cruise.set"), Accent.NEUTRAL, visibleWhen = "has.cruise"),
                Control("bng_cruise_speed", ControlType.ROTARY, "Cruise speed", "cruise", 7, 1, 1, 1, Accent.CYAN,
                    cw = bng("cruise.up"), ccw = bng("cruise.down"), visibleWhen = "has.cruise", feedbackKey = "cruise.speed"),

                Control("bng_gearbox", ControlType.SWITCH, "Gearbox", "gearbox", 0, 2, 4, 1, Accent.CYAN,
                    positions = GEARBOX_MODES.map { Position(it, bng("gearbox.mode"), stateValue = it) },
                    positionKey = "gearbox.mode", visibleWhen = "has.gearbox.modes"),
                momentary("bng_gear_down", "Shift down", "gear_down", 4, 2, bng("gear.down"), Accent.NEUTRAL),
                momentary("bng_gear_up", "Shift up", "gear_up", 5, 2, bng("gear.up"), Accent.NEUTRAL, feedback = "gear"),
                momentary("bng_recover", "Recover", "recover", 6, 2, bng("recover"), Accent.AMBER),
                momentary("bng_reset", "Reset", "reset", 7, 2, bng("reset"), Accent.RED),

                momentary("bng_cam_prev", "Cam prev", "camera", 0, 3, bng("camera.prev")),
                momentary("bng_cam_next", "Cam next", "camera_cycle", 1, 3, bng("camera.next"), feedback = "camera.mode"),
                momentary("bng_look_back", "Look back", "look_back", 2, 3, bng("action.look_back")),
                latching("bng_slowmo", "Slow-mo", "slowmo", 3, 3, bng("slowmo"), Accent.CYAN, feedback = "game.slowmo"),
                momentary("bng_slower", "Slower", "minus", 4, 3, bng("slowmo.slower")),
                momentary("bng_faster", "Faster", "plus", 5, 3, bng("slowmo.faster")),
                latching("bng_pause", "Pause", "pause", 6, 3, bng("pause"), Accent.AMBER, feedback = "game.paused", w = 2),
            ),
        )
        val drivetrain = Page(
            id = "bng_drivetrain", name = "4x4", columns = 8, rows = 4,
            controls = listOf(
                latching("bng_diff_f", "Front diff", "diff_lock", 0, 0, bng("diff.front"), Accent.AMBER, feedback = "diff.front.locked", visibleWhen = "has.diff.front", h = 2),
                latching("bng_diff_c", "Center diff", "differential", 1, 0, bng("diff.center"), Accent.AMBER, feedback = "diff.center.locked", visibleWhen = "has.diff.center", h = 2),
                latching("bng_diff_r", "Rear diff", "diff_lock", 2, 0, bng("diff.rear"), Accent.AMBER, feedback = "diff.rear.locked", visibleWhen = "has.diff.rear", h = 2),
                latching("bng_awd", "4WD", "awd", 3, 0, bng("awd"), Accent.GREEN, feedback = "awd.active", visibleWhen = "has.awd", h = 2),
                latching("bng_range", "Low range", "low_range", 4, 0, bng("range"), Accent.CYAN, feedback = "range.low", visibleWhen = "has.range", h = 2),
                latching("bng_esc2", "ESC / TC", "esc_off", 5, 0, bng("esc"), Accent.AMBER, feedback = "esc.active", visibleWhen = "has.esc", h = 2),
                latching("bng_pbrake2", "Park brake", "parking_brake", 6, 0, bng("parkingbrake"), Accent.RED, feedback = "parkingbrake", w = 2, h = 2),
                Control("bng_gearbox2", ControlType.RADIO, "Gearbox", "gearbox", 0, 2, 6, 1, Accent.CYAN,
                    positions = GEARBOX_MODES.map { Position(it, bng("gearbox.mode"), stateValue = it) },
                    positionKey = "gearbox.mode", visibleWhen = "has.gearbox.modes"),
                momentary("bng_gear_down2", "Shift down", "gear_down", 6, 2, bng("gear.down")),
                momentary("bng_gear_up2", "Shift up", "gear_up", 7, 2, bng("gear.up"), feedback = "gear"),
                momentary("bng_recover2", "Recover", "recover", 0, 3, bng("recover"), Accent.AMBER, w = 2),
                momentary("bng_reset2", "Reset", "reset", 2, 3, bng("reset"), Accent.RED),
                momentary("bng_reset_full", "Full reset", "reset", 3, 3, bng("reset.full"), Accent.RED),
                momentary("bng_horn2", "Horn", "horn", 4, 3, bng("horn")),
                latching("bng_hazard2", "Hazard", "hazard", 5, 3, bng("hazard"), Accent.RED, feedback = "hazard"),
                momentary("bng_cam_next2", "Camera", "camera_cycle", 6, 3, bng("camera.next")),
                momentary("bng_center_cam", "Center cam", "visibility", 7, 3, bng("action.center_camera")),
            ),
        )
        return Profile(
            id = BEAMNG_ID, name = "BeamNG.drive", game = GameId.BEAMNG, builtIn = true,
            pages = listOf(drive, drivetrain), output = OutputRoute.COMPANION,
            description = "Every vehicle function with live lights from the game. Needs the PC companion and the BeamNG mod.",
        )
    }

    // ---- MudRunner: default keyboard layout -------------------------------------------------

    fun mudrunner(): Profile {
        val page = Page(
            id = "mr_main", name = "Truck", columns = 8, rows = 4,
            controls = listOf(
                latching("mr_diff", "Diff lock", "diff_lock", 0, 0, key("Q", trigger = KeyTrigger.TAP), Accent.AMBER, h = 2),
                latching("mr_awd", "AWD", "awd", 1, 0, key("E", trigger = KeyTrigger.TAP), Accent.GREEN, h = 2),
                momentary("mr_gear", "Gear (hold)", "gearbox", 2, 0, key("R"), Accent.CYAN, h = 2),
                momentary("mr_winch", "Winch", "winch", 3, 0, key("F"), Accent.AMBER, h = 2),
                latching("mr_handbrake", "Handbrake", "parking_brake", 4, 0, key("T", trigger = KeyTrigger.TAP), Accent.RED, h = 2),
                latching("mr_lights", "Headlights", "low_beam", 5, 0, key("H", trigger = KeyTrigger.TAP), Accent.GREEN),
                momentary("mr_horn", "Horn", "horn", 5, 1, key("G")),
                momentary("mr_map", "Map", "map", 6, 0, key("M", trigger = KeyTrigger.PRESS), Accent.CYAN, w = 2),
                momentary("mr_camera", "Camera", "camera_cycle", 6, 1, key("C", trigger = KeyTrigger.PRESS), w = 2),
                momentary("mr_up", "Gas", "arrow_up", 1, 2, key("W"), Accent.GREEN),
                momentary("mr_left", "Left", "arrow_left", 0, 3, key("A")),
                momentary("mr_down", "Brake", "arrow_down", 1, 3, key("S"), Accent.RED),
                momentary("mr_right", "Right", "arrow_right", 2, 3, key("D")),
                momentary("mr_escape", "Menu", "menu", 7, 3, key("ESC", trigger = KeyTrigger.PRESS)),
            ),
        )
        return Profile(
            id = MUDRUNNER_ID, name = "MudRunner", game = GameId.MUDRUNNER, builtIn = true,
            pages = listOf(page), output = OutputRoute.AUTO,
            description = "Diff lock, AWD, gears, winch, map and camera on MudRunner's default keys. Edit any key to match your bindings.",
        )
    }
}
