package com.gamecontroller.engine

import com.gamecontroller.hid.HidDescriptor
import com.gamecontroller.hid.HidState
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.KeyTrigger
import com.gamecontroller.model.OutputRoute
import com.gamecontroller.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The two ways input leaves the phone. Implemented by the Android layer (and by fakes in tests). */
interface OutputLinks {
    val hidConnected: Boolean
    val companionConnected: Boolean

    /** Sends one HID input report. [payload] excludes the report id. */
    fun sendHid(reportId: Int, payload: ByteArray)

    /**
     * Sends a control event to the companion. [reliable] events go over the WebSocket,
     * the rest over UDP (see PROTOCOL.md "Which channel carries which event").
     */
    fun sendCompanion(id: String, value: Float, seq: Long, reliable: Boolean)
}

enum class Route { HID, COMPANION, NONE }

/**
 * Turns control changes into output. For controls routed to the companion it only forwards
 * id + value (the companion resolves the action from profile.sync). For controls routed to
 * Bluetooth HID it resolves the action locally and drives [HidState].
 */
class InputEngine(
    private val scope: CoroutineScope,
    private val links: OutputLinks,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var profile: Profile? = null
        set(value) {
            releaseAll()
            field = value
        }

    private val hid = HidState()
    private var seq = 0L
    private val lastValue = HashMap<String, Float>()
    private val lastMoved = HashMap<String, Long>()
    private val routeOf = HashMap<String, Route>()
    private var resendJob: Job? = null

    /** Starts a new companion session: the sequence counter restarts (PROTOCOL.md, Control events). */
    fun resetSequence() {
        seq = 0
    }

    private fun nextSeq() = ++seq

    /** Where [control] goes right now, given the profile's output setting and which links are up. */
    fun routeFor(control: Control): Route {
        val p = profile ?: return Route.NONE
        val hidOk = links.hidConnected && hidCapable(control)
        return when (p.output) {
            OutputRoute.HID -> if (hidOk) Route.HID else if (links.companionConnected && !hidCapable(control)) Route.COMPANION else Route.NONE
            OutputRoute.COMPANION -> if (links.companionConnected) Route.COMPANION else Route.NONE
            OutputRoute.AUTO -> when {
                hidOk -> Route.HID
                links.companionConnected -> Route.COMPANION
                else -> Route.NONE
            }
        }
    }

    /** Control ids that currently go over Bluetooth HID; sent in profile.sync as route "hid". */
    fun hidRoutedIds(): Set<String> =
        profile?.pages?.flatMap { it.controls }?.filter { routeFor(it) == Route.HID }?.map { it.id }?.toSet() ?: emptySet()

    /** Applies a new value for [control]. [value] follows the protocol's per-kind value table. */
    fun onValue(control: Control, value: Float) {
        val previous = lastValue[control.id] ?: restValue(control)
        val discrete = control.type in DISCRETE
        if (discrete && previous == value) return // idempotent, matches the companion's rule
        lastValue[control.id] = value
        if (control.type == ControlType.SLIDER) lastMoved[control.id] = clock()

        val route = routeFor(control)
        routeOf[control.id] = route
        when (route) {
            Route.HID -> applyHid(control, previous, value)
            Route.COMPANION -> sendCompanion(control, value)
            Route.NONE -> Unit
        }
        ensureResendLoop()
    }

    /** One rotary detent per call; [delta] is +1 (clockwise) or -1. */
    fun onRotary(control: Control, delta: Int) {
        val step = if (delta >= 0) 1f else -1f
        when (routeFor(control)) {
            Route.HID -> tapAction(if (step > 0) control.cw else control.ccw, 0, 1)
            Route.COMPANION -> links.sendCompanion(control.id, step, nextSeq(), reliable = true)
            Route.NONE -> Unit
        }
    }

    /** Releases everything this engine holds on both links (profile switch, disconnect, app pause). */
    fun releaseAll() {
        for ((id, v) in lastValue) {
            val control = findControl(id) ?: continue
            if (control.type == ControlType.MOMENTARY && v >= 0.5f && routeOf[id] == Route.COMPANION && links.companionConnected) {
                links.sendCompanion(id, 0f, nextSeq(), reliable = true)
            }
        }
        lastValue.clear()
        lastMoved.clear()
        routeOf.clear()
        hid.releaseAll()
        if (links.hidConnected) {
            links.sendHid(HidDescriptor.KEYBOARD_REPORT_ID, hid.keyboardReport())
            links.sendHid(HidDescriptor.GAMEPAD_REPORT_ID, hid.gamepadReport())
        }
    }

    private fun findControl(id: String) = profile?.pages?.firstNotNullOfOrNull { p -> p.controls.firstOrNull { it.id == id } }

    private fun sendCompanion(control: Control, value: Float) {
        val reliable = control.type in DISCRETE
        links.sendCompanion(control.id, value, nextSeq(), reliable)
        if (!reliable && control.type == ControlType.MOMENTARY && value < 0.5f) {
            // Releases are sent 3 times, 30 ms apart, so one lost datagram cannot leave a key held.
            scope.launch {
                repeat(2) {
                    delay(30)
                    if ((lastValue[control.id] ?: 0f) < 0.5f) links.sendCompanion(control.id, 0f, nextSeq(), false)
                }
            }
        }
    }

    /** Re-sends held momentary values and recently moved sliders every 250 ms over UDP. */
    private fun ensureResendLoop() {
        if (resendJob?.isActive == true) return
        resendJob = scope.launch {
            while (isActive) {
                delay(RESEND_MS)
                val now = clock()
                var anyActive = false
                for ((id, v) in lastValue.toMap()) {
                    if (routeOf[id] != Route.COMPANION) continue
                    val c = findControl(id) ?: continue
                    val active = when (c.type) {
                        ControlType.MOMENTARY -> v >= 0.5f
                        ControlType.SLIDER -> now - (lastMoved[id] ?: 0L) <= SLIDER_RESEND_WINDOW_MS
                        else -> false
                    }
                    if (active && links.companionConnected) {
                        anyActive = true
                        links.sendCompanion(id, v, nextSeq(), reliable = false)
                    }
                }
                if (!anyActive) break
            }
        }
    }

    // ---- Bluetooth HID resolution -------------------------------------------------------------

    private fun applyHid(control: Control, previous: Float, value: Float) {
        when (control.type) {
            ControlType.MOMENTARY, ControlType.LATCHING, ControlType.SLIDER ->
                applyLevel(control.action, previous, value, control.range)
            ControlType.SWITCH, ControlType.RADIO -> {
                val i = value.toInt()
                val action = control.positions.getOrNull(i)?.action ?: return
                tapAction(action, i, control.positions.size)
            }
            ControlType.ROTARY -> Unit // handled by onRotary
        }
    }

    /** A value-driven action: hold/tap/press keys, buttons held while >= 0.5, axes follow the value. */
    private fun applyLevel(action: Action, previous: Float, value: Float, range: AxisRange) {
        val wasOn = previous >= 0.5f
        val isOn = value >= 0.5f
        when (action) {
            is Action.Key -> when (action.trigger) {
                KeyTrigger.HOLD -> if (isOn != wasOn) {
                    if (isOn) keysDown(action.keys) else keysUp(action.keys)
                }
                KeyTrigger.TAP -> if (isOn != wasOn) tapKeys(action.keys)
                KeyTrigger.PRESS -> if (isOn && !wasOn) tapKeys(action.keys)
            }
            is Action.VJoyButton -> if (isOn != wasOn) {
                val changed = if (isOn) hid.buttonDown(action.button) else hid.buttonUp(action.button)
                if (changed) flushGamepad()
            }
            is Action.VJoyAxis -> {
                val r = if (action.range == AxisRange.UNIPOLAR || range == AxisRange.UNIPOLAR) value * 2f - 1f else value
                if (hid.setAxis(action.axis, r)) flushGamepad()
            }
            is Action.Macro -> if (isOn && !wasOn) runMacro(action)
            else -> Unit
        }
    }

    /** A one-shot use of an action: switch positions and rotary detents. */
    private fun tapAction(action: Action, index: Int, count: Int) {
        when (action) {
            is Action.Key -> tapKeys(action.keys)
            is Action.VJoyButton -> scope.launch {
                if (hid.buttonDown(action.button)) flushGamepad()
                delay(BUTTON_PULSE_MS)
                if (hid.buttonUp(action.button)) flushGamepad()
            }
            is Action.VJoyAxis -> {
                val v = if (count > 1) -1f + 2f * index / (count - 1) else 1f
                if (hid.setAxis(action.axis, v)) flushGamepad()
            }
            is Action.Macro -> runMacro(action)
            else -> Unit
        }
    }

    private fun keysDown(keys: List<String>) {
        var changed = false
        keys.forEach { changed = hid.keyDown(it) or changed }
        if (changed) flushKeyboard()
    }

    private fun keysUp(keys: List<String>) {
        var changed = false
        keys.asReversed().forEach { changed = hid.keyUp(it) or changed }
        if (changed) flushKeyboard()
    }

    private fun tapKeys(keys: List<String>, holdMs: Long = TAP_MS) {
        scope.launch {
            // A key still down from a previous tap would swallow this press: wait for it to come up.
            var waited = 0L
            while (keys.any { hid.isKeyDown(it) } && waited < MAX_TAP_WAIT_MS) {
                delay(10)
                waited += 10
            }
            keysDown(keys)
            delay(holdMs)
            keysUp(keys)
        }
    }

    private fun runMacro(macro: Action.Macro) {
        scope.launch {
            for (step in macro.steps) {
                if (step.keys.isNotEmpty()) {
                    keysDown(step.keys)
                    delay(step.holdMs.coerceAtLeast(MIN_MACRO_HOLD_MS).toLong())
                    keysUp(step.keys)
                }
                if (step.delayMs > 0) delay(step.delayMs.toLong())
            }
        }
    }

    private fun flushKeyboard() {
        if (links.hidConnected) links.sendHid(HidDescriptor.KEYBOARD_REPORT_ID, hid.keyboardReport())
    }

    private fun flushGamepad() {
        if (links.hidConnected) links.sendHid(HidDescriptor.GAMEPAD_REPORT_ID, hid.gamepadReport())
    }

    companion object {
        val DISCRETE = setOf(ControlType.LATCHING, ControlType.SWITCH, ControlType.RADIO)
        const val RESEND_MS = 250L
        const val SLIDER_RESEND_WINDOW_MS = 2000L
        const val TAP_MS = 40L
        const val BUTTON_PULSE_MS = 80L
        const val MIN_MACRO_HOLD_MS = 20
        const val MAX_TAP_WAIT_MS = 200L

        /** The value a control rests at before the user touches it. */
        fun restValue(control: Control): Float = 0f

        /** True when every action on [control] can be produced by the "GameCtl Pad" HID device. */
        fun hidCapable(control: Control): Boolean {
            val actions = when (control.type) {
                ControlType.SWITCH, ControlType.RADIO -> control.positions.map { it.action }
                ControlType.ROTARY -> listOf(control.cw, control.ccw)
                else -> listOf(control.action)
            }.filter { it !is Action.None }
            return actions.isNotEmpty() && actions.all { hidAction(it) }
        }

        fun hidAction(action: Action): Boolean = when (action) {
            is Action.Key -> action.keys.isNotEmpty()
            is Action.VJoyButton -> action.device == 1 && action.button in 1..HidDescriptor.BUTTONS
            is Action.VJoyAxis -> action.device == 1 && action.axis.uppercase() in HidDescriptor.AXIS_NAMES
            is Action.Macro -> action.steps.isNotEmpty()
            else -> false
        }
    }
}
