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
    const val BEAMNG_PAD_ID = "builtin-beamng-pad"
    const val BULLDOZER_ID = "builtin-bulldozer"
    const val UTILITY_ID = "builtin-utility"

    val all: List<Profile> by lazy { listOf(gamepad(), keyboard(), beamng(), beamngPad(), mudrunner(), bulldozer(), utility()) }

    // ---- small builders --------------------------------------------------------------------

    private fun bng(cmd: String) = Action.BeamNG(cmd)
    private fun key(vararg k: String, trigger: KeyTrigger = KeyTrigger.HOLD) = Action.Key(k.toList(), trigger)
    private fun btn(n: Int) = Action.VJoyButton(1, n)

    private fun momentary(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent = Accent.NEUTRAL, w: Int = 1, h: Int = 1, feedback: String? = null, visibleWhen: String? = null) =
        Control(id, ControlType.MOMENTARY, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    private fun latching(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent, feedback: String? = null, visibleWhen: String? = null, w: Int = 1, h: Int = 1) =
        Control(id, ControlType.LATCHING, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    /** A hydraulic or drive lever: centred, springs back to stop when let go. */
    private fun lever(id: String, label: String, icon: String?, x: Int, y: Int, axis: String, accent: Accent = Accent.CYAN, w: Int = 1, h: Int = 4) =
        Control(id, ControlType.SLIDER, label, icon, x, y, w, h, accent, Action.VJoyAxis(1, axis),
            range = AxisRange.BIPOLAR, springReturn = true, vertical = h >= w)

    /** A throttle: stays where it is left, from idle (bottom) to full. */
    private fun throttle(id: String, x: Int, y: Int, axis: String = "SL0", h: Int = 4) =
        Control(id, ControlType.SLIDER, "Throttle", "speed", x, y, 1, h, Accent.GREEN,
            Action.VJoyAxis(1, axis, AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR)

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
                Control("gp_mode", ControlType.SWITCH, "Mode", "settings", 2, 3, 3, 1, Accent.AMBER,
                    positions = listOf(Position("1", btn(17)), Position("2", btn(18)), Position("3", btn(19)))),
                Control("gp_dial", ControlType.ROTARY, "Dial", null, 6, 2, 2, 2, Accent.NEUTRAL,
                    cw = btn(20), ccw = btn(21)),
                momentary("gp_b22", "B22", null, 5, 3, btn(22)),
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
                momentary("kb_screenshot", "Screenshot", "screenshot", 3, 2, key("PRINTSCREEN", trigger = KeyTrigger.PRESS)),
                momentary("kb_mute", "Push to talk", "mic", 3, 3, key("F13"), Accent.AMBER),
                Control("kb_volume", ControlType.ROTARY, "Volume", "volume_up", 4, 2, 2, 2, Accent.CYAN,
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
                latching("bng_pbrake", "Park brake", "parking_brake", 6, 0, bng("parkingbrake"), Accent.RED, feedback = "parkingbrake"),
                momentary("bng_recover", "Recover", "recover", 7, 0, bng("recover"), Accent.AMBER),

                latching("bng_low", "Low beam", "low_beam", 0, 1, bng("lights.low"), Accent.GREEN, feedback = "lights.low"),
                latching("bng_high", "High beam", "high_beam", 1, 1, bng("lights.high"), Accent.BLUE, feedback = "lights.high"),
                latching("bng_fog", "Fog", "fog_light", 2, 1, bng("lights.fog"), Accent.GREEN, feedback = "lights.fog", visibleWhen = "has.fog"),
                latching("bng_lightbar", "Light bar", "light_bar", 3, 1, bng("lights.light_bar"), Accent.AMBER, feedback = "lights.light_bar", visibleWhen = "has.light_bar"),
                latching("bng_cruise", "Cruise", "cruise", 4, 1, bng("cruise"), Accent.GREEN, feedback = "cruise.active", visibleWhen = "has.cruise"),
                momentary("bng_cruise_set", "Set", "speed", 5, 1, bng("cruise.set"), Accent.NEUTRAL, visibleWhen = "has.cruise"),
                Control("bng_cruise_speed", ControlType.ROTARY, "Cruise speed", "cruise", 6, 1, 2, 2, Accent.CYAN,
                    cw = bng("cruise.up"), ccw = bng("cruise.down"), visibleWhen = "has.cruise", feedbackKey = "cruise.speed"),

                Control("bng_gearbox", ControlType.SWITCH, "Gearbox", "gearbox", 0, 2, 6, 1, Accent.CYAN,
                    positions = GEARBOX_MODES.map { Position(it, bng("gearbox.mode"), stateValue = it) },
                    positionKey = "gearbox.mode", visibleWhen = "has.gearbox.modes"),
                momentary("bng_gear_down", "Shift down", "gear_down", 4, 3, bng("gear.down"), Accent.NEUTRAL),
                momentary("bng_gear_up", "Shift up", "gear_up", 5, 3, bng("gear.up"), Accent.NEUTRAL, feedback = "gear"),

                momentary("bng_cam_prev", "Cam prev", "camera", 0, 3, bng("camera.prev")),
                momentary("bng_cam_next", "Cam next", "camera_cycle", 1, 3, bng("camera.next"), feedback = "camera.mode"),
                momentary("bng_look_back", "Look back", "look_back", 2, 3, bng("action.look_back")),
                latching("bng_slowmo", "Slow-mo", "slowmo", 3, 3, bng("slowmo"), Accent.CYAN, feedback = "game.slowmo"),
                latching("bng_pause", "Pause", "pause", 6, 3, bng("pause"), Accent.AMBER, feedback = "game.paused"),
                momentary("bng_reset", "Reset", "reset", 7, 3, bng("reset"), Accent.RED),
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

    // ---- BeamNG over Bluetooth: gamepad buttons bound in-game by "Bind in game" -------------

    /**
     * Same functions as [beamng], but every control is a gamepad button that BeamNG reads directly
     * (Bluetooth "GameCtl Pad" or vJoy). "Bind in game" asks the mod to map each button to its
     * BeamNG action once. Lamps still light from game state when the companion and mod are running.
     */
    fun beamngPad(): Profile {
        var n = 0
        fun pad(id: String, label: String, icon: String, x: Int, y: Int, bind: String, accent: Accent = Accent.NEUTRAL, feedback: String? = null, visibleWhen: String? = null, w: Int = 1) =
            Control("bpad_$id", ControlType.MOMENTARY, label, icon, x, y, w, 1, accent, btn(++n), feedbackKey = feedback, visibleWhen = visibleWhen, bindTo = bind)
        val page = Page(
            id = "bpad_main", name = "Vehicle", columns = 8, rows = 4,
            controls = listOf(
                pad("starter", "Start", "starter", 0, 0, "activateStarterMotor", Accent.RED, "engine.running"),
                pad("horn", "Horn", "horn", 1, 0, "horn"),
                pad("hazard", "Hazard", "hazard", 2, 0, "toggle_hazard_signal", Accent.RED, "hazard"),
                pad("sig_l", "Left", "signal_left", 3, 0, "toggle_left_signal", Accent.GREEN, "signal.left"),
                pad("sig_r", "Right", "signal_right", 4, 0, "toggle_right_signal", Accent.GREEN, "signal.right"),
                pad("pbrake", "Park brake", "parking_brake", 5, 0, "parkingbrake_toggle", Accent.RED, "parkingbrake"),
                pad("lights", "Headlights", "low_beam", 6, 0, "toggle_headlights", Accent.GREEN, "lights.low"),
                pad("fog", "Fog", "fog_light", 7, 0, "toggle_foglights", Accent.GREEN, "lights.fog", "has.fog"),
                pad("esc", "ESC / TC", "esc", 0, 1, "toggleESCMode", Accent.AMBER, "esc.active", "has.esc"),
                pad("diff", "Diff mode", "diff_lock", 1, 1, "toggleDiffMode", Accent.AMBER, "diff.rear.locked"),
                pad("awd", "4WD", "awd", 2, 1, "toggle4WDStatus", Accent.GREEN, "awd.active", "has.awd"),
                pad("range", "Low range", "low_range", 3, 1, "toggleRangeStatus", Accent.CYAN, "range.low", "has.range"),
                pad("shifter", "Shift mode", "gearbox", 4, 1, "toggleShifterMode", Accent.CYAN, "gearbox.mode"),
                pad("gear_down", "Shift down", "gear_down", 5, 1, "shiftDown"),
                pad("gear_up", "Shift up", "gear_up", 6, 1, "shiftUp", feedback = "gear"),
                pad("lightbar", "Light bar", "light_bar", 7, 1, "toggle_lightbar_signal", Accent.AMBER, "lights.light_bar", "has.light_bar"),
                pad("cam", "Camera", "camera_cycle", 0, 2, "switch_camera_next", feedback = "camera.mode"),
                pad("look_back", "Look back", "look_back", 1, 2, "look_back"),
                pad("center_cam", "Center cam", "visibility", 2, 2, "center_camera"),
                pad("recover", "Recover", "recover", 3, 2, "recover_vehicle", Accent.AMBER),
                pad("reset", "Reset", "reset", 4, 2, "reset_physics", Accent.RED),
                pad("home", "Load home", "home", 5, 2, "loadHome"),
                pad("save_home", "Save home", "place", 6, 2, "saveHome"),
                pad("reload", "Reload", "build", 7, 2, "reload_vehicle"),
                pad("slowmo", "Slow-mo", "slowmo", 0, 3, "toggle_slow_motion", Accent.CYAN, "game.slowmo"),
                pad("slower", "Slower", "minus", 1, 3, "slower_motion"),
                pad("faster", "Faster", "plus", 2, 3, "faster_motion"),
                pad("pause", "Pause", "pause", 3, 3, "pause", Accent.AMBER, "game.paused"),
                pad("full_reset", "Full reset", "reset", 4, 3, "reset_all_physics", Accent.RED),
            ) + listOf(
                Control("bpad_info_gear", ControlType.MOMENTARY, "Gear", "gearbox", 5, 3, 3, 1, Accent.CYAN,
                    Action.None, feedbackKey = "gear"),
            ),
        )
        return Profile(
            id = BEAMNG_PAD_ID, name = "BeamNG.drive (Bluetooth pad)", game = GameId.BEAMNG, builtIn = true,
            pages = listOf(page), output = OutputRoute.AUTO,
            description = "Every control is a gamepad button. Tap \"Bind in game\" in the editor once (needs the mod) or bind them in BeamNG's Controls menu.",
        )
    }

    // ---- MudRunner: default keyboard layout -------------------------------------------------

    fun mudrunner(): Profile {
        // Fills the whole 8x4 grid: pedals and steering on the left, the three big lit toggles on top,
        // gear / handbrake / lights / horn in the middle row, map / camera / menu along the bottom.
        val page = Page(
            id = "mr_main", name = "Truck", columns = 8, rows = 4,
            controls = listOf(
                momentary("mr_up", "Gas", "arrow_up", 0, 0, key("W"), Accent.GREEN, h = 2),
                momentary("mr_down", "Brake", "arrow_down", 0, 2, key("S"), Accent.RED, h = 2),
                momentary("mr_left", "Left", "arrow_left", 1, 0, key("A"), h = 2),
                momentary("mr_right", "Right", "arrow_right", 1, 2, key("D"), h = 2),
                latching("mr_diff", "Diff lock", "diff_lock", 2, 0, key("Q", trigger = KeyTrigger.TAP), Accent.AMBER, w = 2, h = 2),
                latching("mr_awd", "AWD", "awd", 4, 0, key("E", trigger = KeyTrigger.TAP), Accent.GREEN, w = 2, h = 2),
                momentary("mr_winch", "Winch (hold)", "winch", 6, 0, key("F"), Accent.AMBER, w = 2, h = 2),
                momentary("mr_gear", "Gear (hold)", "gearbox", 2, 2, key("R"), Accent.CYAN, w = 2),
                latching("mr_handbrake", "Handbrake", "parking_brake", 4, 2, key("T", trigger = KeyTrigger.TAP), Accent.RED, w = 2),
                latching("mr_lights", "Lights", "low_beam", 6, 2, key("H", trigger = KeyTrigger.TAP), Accent.GREEN),
                momentary("mr_horn", "Horn", "horn", 7, 2, key("G")),
                momentary("mr_map", "Map", "map", 2, 3, key("M", trigger = KeyTrigger.PRESS), Accent.CYAN, w = 2),
                momentary("mr_camera", "Camera", "camera_cycle", 4, 3, key("C", trigger = KeyTrigger.PRESS), w = 2),
                momentary("mr_escape", "Menu", "menu", 6, 3, key("ESC", trigger = KeyTrigger.PRESS), w = 2),
            ),
        )
        return Profile(
            id = MUDRUNNER_ID, name = "MudRunner", game = GameId.MUDRUNNER, builtIn = true,
            pages = listOf(page), output = OutputRoute.AUTO,
            description = "Diff lock, AWD, winch, gears, handbrake, map and camera on MudRunner's default keys. Edit any key to match your bindings.",
        )
    }

    // ---- Heavy machines: pilot joysticks, levers and buttons, bound once in the game ----------
    //
    // Laid out like a real cab: a pilot joystick under each thumb (two spring-centred axes each,
    // ISO pattern on the excavator), extra hydraulics as spring-centred levers, switchgear in the
    // middle. Every axis and button is the Bluetooth pad (or vJoy device 1), so these work in any
    // game with machines once bound in its controls menu. Buttons keep one number per job across
    // machines (1 start, 2 horn, 3 beacon, 4 lights, 5 work lights, 6 park brake, 7/8 gear down/up,
    // 11/12 cameras), so one set of bindings covers several machines. Jobs only one machine has use
    // 17-32 and may share a number with another machine's own job (24 is excavator travel speed and the
    // plough's spreader), since each machine is bound on its own. Every button is 32 or below,
    // the most many DirectInput games and the Windows Game Controllers panel can see.

    private fun stick(id: String, label: String, icon: String?, x: Int, y: Int, xAxis: String, yAxis: String, accent: Accent = Accent.AMBER) =
        Control(id, ControlType.JOYSTICK, label, icon, x, y, 2, 2, accent, Action.VJoyAxis(1, xAxis), actionY = Action.VJoyAxis(1, yAxis))

    private fun direction(id: String, x: Int, y: Int) =
        Control(id, ControlType.SWITCH, "Direction", "direction", x, y, 3, 1, Accent.CYAN,
            positions = listOf(Position("F", btn(13)), Position("N", btn(14)), Position("R", btn(15))))

    fun bulldozer(): Profile {
        val dozer = Page(
            id = "dz_main", name = "Dozer", columns = 8, rows = 4,
            controls = listOf(
                stick("dz_drive", "Drive / steer", "tracks", 0, 0, "X", "Y", Accent.GREEN),
                throttle("dz_throttle", 0, 2, h = 2),
                momentary("dz_gear_up", "Gear up", "gear_up", 1, 2, btn(8)),
                momentary("dz_gear_down", "Gear down", "gear_down", 1, 3, btn(7)),
                momentary("dz_start", "Start", "starter", 2, 0, btn(1), Accent.RED),
                momentary("dz_horn", "Horn", "horn", 3, 0, btn(2)),
                latching("dz_beacon", "Beacon", "beacon", 4, 0, btn(3), Accent.AMBER),
                latching("dz_lights", "Lights", "low_beam", 2, 1, btn(4), Accent.GREEN),
                latching("dz_work", "Work lights", "work_light", 3, 1, btn(5), Accent.AMBER),
                latching("dz_pbrake", "Park brake", "parking_brake", 4, 1, btn(6), Accent.RED),
                direction("dz_direction", 2, 2),
                latching("dz_float", "Blade float", "float_mode", 2, 3, btn(9), Accent.CYAN),
                latching("dz_autoshift", "Auto shift", "gearbox", 3, 3, btn(16), Accent.GREEN),
                momentary("dz_cam", "Camera", "camera_cycle", 4, 3, btn(11)),
                lever("dz_ripper", "Ripper", "ripper", 5, 0, "SL1", Accent.AMBER),
                stick("dz_blade", "Blade lift / tilt", "blade", 6, 0, "RY", "RX"),
                lever("dz_angle", "Blade angle", "blade_angle", 6, 2, "RZ", w = 2, h = 1),
                momentary("dz_shank", "Ripper pin", "ripper", 6, 3, btn(10), Accent.AMBER),
                momentary("dz_rear_cam", "Rear view", "look_back", 7, 3, btn(12)),
            ),
        )
        return Profile(
            id = BULLDOZER_ID, name = "Bulldozer", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(dozer), output = OutputRoute.AUTO,
            description = "A dozer cab: drive joystick on the left, blade joystick on the right, ripper and blade-angle levers, throttle and switchgear. Bind once in the game's controls menu.",
        )
    }

    fun utility(): Profile {
        val loader = Page(
            id = "ut_loader", name = "Loader", columns = 8, rows = 4,
            controls = listOf(
                stick("ld_drive", "Drive / steer", "arrow_up", 0, 0, "X", "Y", Accent.GREEN),
                throttle("ld_throttle", 0, 2, h = 2),
                momentary("ld_gear_up", "Gear up", "gear_up", 1, 2, btn(8)),
                momentary("ld_gear_down", "Gear down", "gear_down", 1, 3, btn(7)),
                momentary("ld_start", "Start", "starter", 2, 0, btn(1), Accent.RED),
                momentary("ld_horn", "Horn", "horn", 3, 0, btn(2)),
                latching("ld_beacon", "Beacon", "beacon", 4, 0, btn(3), Accent.AMBER),
                latching("ld_lights", "Lights", "low_beam", 5, 0, btn(4), Accent.GREEN),
                latching("ld_pbrake", "Park brake", "parking_brake", 2, 1, btn(6), Accent.RED),
                latching("ld_diff", "Diff lock", "diff_lock", 3, 1, btn(32), Accent.AMBER),
                latching("ld_float", "Float", "float_mode", 4, 1, btn(9), Accent.CYAN),
                latching("ld_coupler", "Coupler", "quick_coupler", 5, 1, btn(19), Accent.AMBER),
                momentary("ld_return", "Return to dig", "bucket", 2, 2, btn(17), Accent.CYAN),
                momentary("ld_cam", "Camera", "camera_cycle", 3, 2, btn(11)),
                momentary("ld_rear_cam", "Rear view", "look_back", 4, 2, btn(12)),
                latching("ld_work", "Work lights", "work_light", 5, 2, btn(5), Accent.AMBER),
                direction("ld_direction", 2, 3),
                momentary("ld_kickdown", "Kickdown", "gear_down", 5, 3, btn(18)),
                stick("ld_bucket", "Boom / bucket", "bucket", 6, 0, "RY", "RX"),
                stick("ld_aux", "Attachment", "quick_coupler", 6, 2, "RZ", "SL1", Accent.CYAN),
            ),
        )
        val excavator = Page(
            id = "ut_excavator", name = "Excavator", columns = 8, rows = 4,
            controls = listOf(
                lever("ex_left", "Left track", "tracks", 0, 0, "Y", Accent.GREEN),
                lever("ex_right", "Right track", "tracks", 1, 0, "Z", Accent.GREEN),
                stick("ex_left_stick", "Swing / stick", "swing", 2, 0, "X", "RY"),
                momentary("ex_start", "Start", "starter", 4, 0, btn(1), Accent.RED),
                momentary("ex_horn", "Horn", "horn", 5, 0, btn(2)),
                latching("ex_beacon", "Beacon", "beacon", 4, 1, btn(3), Accent.AMBER),
                latching("ex_lights", "Lights", "low_beam", 5, 1, btn(4), Accent.GREEN),
                stick("ex_right_stick", "Boom / bucket", "boom", 6, 0, "SL1", "RX"),
                latching("ex_work", "Work lights", "work_light", 2, 2, btn(5), Accent.AMBER),
                momentary("ex_hammer", "Hammer", "bucket", 3, 2, btn(20), Accent.RED),
                latching("ex_coupler", "Coupler", "quick_coupler", 4, 2, btn(19), Accent.AMBER),
                momentary("ex_cam", "Camera", "camera_cycle", 5, 2, btn(11)),
                lever("ex_aux", "Aux flow", "stick", 2, 3, "RZ", Accent.CYAN, w = 2, h = 1),
                latching("ex_hispeed", "Travel speed", "tracks", 4, 3, btn(24), Accent.GREEN),
                momentary("ex_rear_cam", "Rear view", "look_back", 5, 3, btn(12)),
                Control("ex_rpm", ControlType.ROTARY, "Engine speed", "speed", 6, 2, 2, 2, Accent.CYAN, cw = btn(25), ccw = btn(26)),
            ),
        )
        val crane = Page(
            id = "ut_crane", name = "Crane", columns = 8, rows = 4,
            controls = listOf(
                stick("cr_left_stick", "Slew / hoist", "hook", 0, 0, "X", "SL1"),
                stick("cr_drive", "Drive / steer", "arrow_up", 0, 2, "Z", "Y", Accent.GREEN),
                momentary("cr_out_out", "Outriggers out", "outriggers", 2, 0, btn(21), Accent.AMBER),
                momentary("cr_out_in", "Outriggers in", "outriggers", 3, 0, btn(22)),
                latching("cr_lock", "Load lock", "hook", 4, 0, btn(23), Accent.RED),
                momentary("cr_start", "Start", "starter", 5, 0, btn(1), Accent.RED),
                momentary("cr_horn", "Horn", "horn", 2, 1, btn(2)),
                latching("cr_beacon", "Beacon", "beacon", 3, 1, btn(3), Accent.AMBER),
                latching("cr_lights", "Lights", "low_beam", 4, 1, btn(4), Accent.GREEN),
                latching("cr_pbrake", "Park brake", "parking_brake", 5, 1, btn(6), Accent.RED),
                momentary("cr_gear_down", "Gear down", "gear_down", 2, 2, btn(7)),
                momentary("cr_gear_up", "Gear up", "gear_up", 3, 2, btn(8)),
                momentary("cr_cam", "Camera", "camera_cycle", 4, 2, btn(11)),
                momentary("cr_hook_cam", "Hook view", "camera", 5, 2, btn(10)),
                lever("cr_rotate", "Hook rotate", "hook", 2, 3, "RZ", Accent.NEUTRAL, w = 4, h = 1),
                stick("cr_right_stick", "Boom / extend", "boom", 6, 0, "RY", "RX"),
                throttle("cr_throttle", 6, 2, h = 2),
                latching("cr_work", "Work lights", "work_light", 7, 2, btn(5), Accent.AMBER),
                momentary("cr_rear_cam", "Rear view", "look_back", 7, 3, btn(12)),
            ),
        )
        val plough = Page(
            id = "ut_plough", name = "Plough & tow", columns = 8, rows = 4,
            controls = listOf(
                stick("pl_plough", "Plough lift / angle", "plough", 0, 0, "Z", "RX"),
                Control("pl_rate", ControlType.ROTARY, "Spread rate", "spreader", 0, 2, 2, 2, Accent.CYAN, cw = btn(25), ccw = btn(26)),
                latching("pl_beacon", "Beacon", "beacon", 2, 0, btn(3), Accent.AMBER),
                latching("pl_lights", "Lights", "low_beam", 3, 0, btn(4), Accent.GREEN),
                latching("pl_work", "Work lights", "work_light", 4, 0, btn(5), Accent.AMBER),
                momentary("pl_horn", "Horn", "horn", 5, 0, btn(2)),
                latching("pl_spreader", "Spreader", "spreader", 2, 1, btn(24), Accent.CYAN),
                latching("pl_float", "Plough float", "float_mode", 3, 1, btn(31), Accent.CYAN),
                momentary("pl_wheel_up", "Wheel lift up", "tow", 4, 1, btn(27), Accent.AMBER),
                momentary("pl_wheel_down", "Wheel lift down", "tow", 5, 1, btn(28)),
                momentary("pl_winch_in", "Winch in", "winch", 2, 2, btn(29), Accent.AMBER),
                momentary("pl_winch_out", "Winch out", "winch", 3, 2, btn(30)),
                latching("pl_pbrake", "Park brake", "parking_brake", 4, 2, btn(6), Accent.RED),
                momentary("pl_cam", "Camera", "camera_cycle", 5, 2, btn(11)),
                momentary("pl_rear_cam", "Rear view", "look_back", 2, 3, btn(12)),
                stick("pl_tow", "Tow boom / extend", "tow", 6, 0, "RY", "SL1"),
                stick("pl_wing", "Wing plough", "plough", 6, 2, "RZ", "X", Accent.CYAN),
            ),
        )
        return Profile(
            id = UTILITY_ID, name = "Utility vehicles", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(loader, excavator, crane, plough), output = OutputRoute.AUTO,
            description = "Wheel loader, excavator (ISO joysticks), mobile crane, snow plough and tow truck, a page each. Bind once in the game's controls menu.",
        )
    }
}
