package com.gamecontroller.engine

import com.gamecontroller.hid.HidDescriptor
import com.gamecontroller.hid.HidState
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.Joystick
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
 *
 * Invariant: every held input is released on every exit path (profile switch, link loss,
 * route change, app pause, control becoming unavailable). See [releaseAll] and [onLinkChanged].
 */
class InputEngine(
    private val scope: CoroutineScope,
    private val links: OutputLinks,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var profile: Profile? = null
        set(value) {
            releaseAll()
            lastValue.clear()
            lastMoved.clear()
            routeOf.clear()
            field = value
            resetAxes()
        }

    private val hid = HidState()
    private var seq = 0L
    private val lastValue = HashMap<String, Float>()
    private val lastMoved = HashMap<String, Long>()
    /** Route each control's current (held) value went out on, so its release follows the same path. */
    private val routeOf = HashMap<String, Route>()
    private var resendJob: Job? = null
    private val tapJobs = ArrayList<Job>()
    private var pulseQueue: Job? = null
    private val pendingPulses = ArrayDeque<Int>()

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
        profile?.pages?.flatMap { Joystick.expand(it.controls) }?.filter { routeFor(it) == Route.HID }?.map { it.id }?.toSet() ?: emptySet()

    /** The current HID input report for [reportId] (keyboard or gamepad), for GET_REPORT requests. */
    fun hidReport(reportId: Int): ByteArray? = when (reportId) {
        HidDescriptor.KEYBOARD_REPORT_ID -> hid.keyboardReport()
        HidDescriptor.GAMEPAD_REPORT_ID -> hid.gamepadReport()
        else -> null
    }

    /** The value the engine believes [id] has right now (what the PC was last told). */
    fun currentValue(id: String): Float? = lastValue[id]

    /**
     * Tells the engine the game changed a control's state behind its back (feedback sync), so the
     * next user press is compared against what the game shows, not against the last thing we sent.
     * Nothing is sent.
     */
    fun syncValue(id: String, value: Float) {
        val previous = lastValue[id]
        lastValue[id] = value
        if (previous == null || previous == value) return
        // A hold the phone drives over HID must let go when the game says off, or the refcount drifts (A21).
        // Only releases are applied: a game-initiated ON must not press a button the user never touched (A29).
        val c = findControl(id) ?: return
        // "Off" for a latch, switch or radio is its rest position; isRelease only knows momentary and slider.
        if (routeOf[id] != Route.HID || value != restValue(c)) return
        when (val a = c.action) {
            is Action.VJoyButton, is Action.VJoyAxis -> applyLevel(c, a, previous, value)
            is Action.Key -> if (a.trigger == KeyTrigger.HOLD) applyLevel(c, a, previous, value)
            else -> Unit
        }
        routeOf.remove(id)
    }

    /** True while the engine still drives [id] on a live link (a held button, a moved slider, a latch). */
    fun isHeld(id: String): Boolean {
        val route = routeOf[id] ?: return false
        if (route == Route.NONE) return false
        val c = findControl(id) ?: return false
        return (lastValue[id] ?: restValue(c)) != restValue(c)
    }

    /** Applies a new value for [control]. [value] follows the protocol's per-kind value table. */
    fun onValue(control: Control, value: Float) {
        val previous = lastValue[control.id] ?: restValue(control)
        val release = isRelease(control, value)
        lastValue[control.id] = value
        if (control.type == ControlType.SLIDER) lastMoved[control.id] = clock()

        // A release always goes out on the route its press used (A3); a press picks the current route.
        // A release with nothing held (the link that carried the press already dropped) goes nowhere.
        val route = if (release) routeOf[control.id] ?: Route.NONE else routeFor(control)
        val oldRoute = routeOf[control.id]
        var prev = previous
        val parkable = control.type == ControlType.MOMENTARY || control.type == ControlType.SLIDER
        if (!release && parkable && oldRoute != null && oldRoute != route && prev != restValue(control)) {
            // The route changed under a held button or a moved slider: let go on the old route first (A24).
            // Discrete controls are not parked: their rest is a real position, so sending it would tap it (A28).
            val rest = restValue(control)
            when (oldRoute) {
                Route.HID -> applyHid(control, prev, rest)
                Route.COMPANION -> if (links.companionConnected) links.sendCompanion(control.id, rest, nextSeq(), reliable = true)
                Route.NONE -> Unit
            }
            prev = rest
        }
        routeOf[control.id] = route
        when (route) {
            Route.HID -> applyHid(control, prev, value)
            Route.COMPANION -> sendCompanion(control, value)
            Route.NONE -> Unit
        }
        if (release) routeOf.remove(control.id)
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

    /**
     * A link came up or went down. Held inputs that were travelling on a link that dropped are
     * forgotten locally (the other end releases on its own: companion auto-release / HID disconnect),
     * and a link that came up gets a clean report so nothing stale is held.
     */
    fun onLinkChanged() {
        val p = profile
        if (!links.hidConnected) {
            hid.releaseAll()
            resetAxesState()
            if (p != null) forgetHeld(Route.HID)
        } else {
            // Fresh connection: make sure the PC sees exactly what we hold (usually nothing).
            flushKeyboard()
            flushGamepad()
        }
        if (!links.companionConnected && p != null) forgetHeld(Route.COMPANION)
    }

    /**
     * The companion link went down. Its holds are forgotten even if [OutputLinks.companionConnected] still
     * reads true while the client tears down, so the next welcome replays every latch that is on (M1).
     */
    fun onCompanionLost() {
        profile?.let { forgetHeld(Route.COMPANION) }
        onLinkChanged()
    }

    /**
     * Moves latches that are on to the route they would take now (A-S1). Called after a link comes up:
     * the old route's owner has already let them go (the companion releases ids that profile.sync marks
     * as HID-routed), so the latch is pressed again on its new route and the PC stays in step with the cap.
     */
    fun rehomeLatches() {
        for ((id, route) in routeOf.toMap()) {
            val c = findControl(id) ?: continue
            if (c.type != ControlType.LATCHING) continue
            val v = lastValue[id] ?: continue
            val rest = restValue(c)
            if (v == rest) continue
            val target = routeFor(c)
            if (target == route) continue
            when (target) {
                Route.HID -> { routeOf[id] = target; applyHid(c, rest, v) }
                Route.COMPANION -> { routeOf[id] = target; sendCompanion(c, v) }
                Route.NONE -> routeOf.remove(id)
            }
        }
    }

    private fun forgetHeld(route: Route) {
        val ids = routeOf.filterValues { it == route }.keys.toList()
        for (id in ids) {
            routeOf.remove(id)
            val c = findControl(id) ?: continue
            if (c.type == ControlType.MOMENTARY) lastValue[id] = 0f
            if (c.type == ControlType.SLIDER) lastValue[id] = restValue(c)
        }
    }

    /** Releases everything this engine holds on both links (profile switch, disconnect, app pause). */
    fun releaseAll() {
        tapJobs.forEach { it.cancel() }
        tapJobs.clear()
        pulseQueue?.cancel()
        pulseQueue = null
        pendingPulses.clear()
        resendJob?.cancel()
        resendJob = null
        for ((id, v) in lastValue.toMap()) {
            val control = findControl(id) ?: continue
            val held = when (control.type) {
                ControlType.MOMENTARY -> v >= 0.5f
                ControlType.SLIDER -> v != restValue(control)
                else -> false
            }
            if (!held) continue
            val rest = restValue(control)
            if (routeOf[id] == Route.COMPANION && links.companionConnected) {
                links.sendCompanion(id, rest, nextSeq(), reliable = true)
            }
            lastValue[id] = rest
        }
        lastMoved.clear()
        routeOf.clear()
        hid.releaseAll()
        resetAxesState()
        flushKeyboard()
        flushGamepad()
    }

    /** Puts unipolar HID axes at their rest position (-1 = 0 %), bipolar ones at centre (A4). */
    private fun resetAxes() {
        resetAxesState()
        flushGamepad()
    }

    private fun resetAxesState() {
        val p = profile ?: return
        for (c in p.pages.flatMap { Joystick.expand(it.controls) }) {
            if (c.type != ControlType.SLIDER) continue
            val a = c.action as? Action.VJoyAxis ?: continue
            if (!hidAction(a)) continue
            hid.setAxis(a.axis, if (isUnipolar(c, a)) -1f else 0f)
        }
    }

    private fun findControl(id: String) = profile?.pages?.firstNotNullOfOrNull { p -> Joystick.find(p.controls, id) }

    private fun sendCompanion(control: Control, value: Float) {
        val reliable = control.type in DISCRETE
        links.sendCompanion(control.id, value, nextSeq(), reliable)
        if (!reliable && control.type == ControlType.MOMENTARY && value < 0.5f) {
            // Releases are sent 3 times, 30 ms apart, so one lost datagram cannot leave a key held.
            scope.launch {
                repeat(2) {
                    delay(30)
                    if ((lastValue[control.id] ?: 0f) < 0.5f && links.companionConnected) {
                        links.sendCompanion(control.id, 0f, nextSeq(), false)
                    }
                }
            }
        }
    }

    /** Re-sends held momentary values and recently moved sliders every 250 ms over UDP. */
    private fun ensureResendLoop() {
        if (resendJob?.isActive == true) return
        resendJob = scope.launch {
            var idle = 0
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
                    if (active) {
                        anyActive = true
                        if (links.companionConnected) links.sendCompanion(id, v, nextSeq(), reliable = false)
                    }
                }
                // Keep looping through a brief companion drop while something is still held (A15).
                idle = if (anyActive) 0 else idle + 1
                if (idle >= 4) break
            }
        }
    }

    // ---- Bluetooth HID resolution -------------------------------------------------------------

    private fun applyHid(control: Control, previous: Float, value: Float) {
        when (control.type) {
            ControlType.MOMENTARY, ControlType.LATCHING, ControlType.SLIDER ->
                applyLevel(control, control.action, previous, value)
            ControlType.SWITCH, ControlType.RADIO -> {
                val i = value.toInt()
                val action = control.positions.getOrNull(i)?.action ?: return
                tapAction(action, i, control.positions.size)
            }
            ControlType.ROTARY -> Unit // handled by onRotary
            ControlType.JOYSTICK -> Unit // the UI sends its two axes as sliders (Joystick)
        }
    }

    /** A value-driven action: hold/tap/press keys, buttons held while >= 0.5, axes follow the value. */
    private fun applyLevel(control: Control, action: Action, previous: Float, value: Float) {
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
                val r = if (isUnipolar(control, action)) value * 2f - 1f else value
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
            is Action.VJoyButton -> pulseButton(action.button)
            is Action.VJoyAxis -> {
                val v = if (count > 1) -1f + 2f * index / (count - 1) else 1f
                if (hid.setAxis(action.axis, v)) flushGamepad()
            }
            is Action.Macro -> runMacro(action)
            else -> Unit
        }
    }

    /** Button pulses are queued so rapid rotary detents each reach the game as a separate press (A14). */
    private fun pulseButton(button: Int) {
        pendingPulses.addLast(button)
        if (pulseQueue?.isActive == true) return
        pulseQueue = scope.launch {
            while (pendingPulses.isNotEmpty()) {
                val b = pendingPulses.removeFirst()
                if (hid.buttonDown(b)) flushGamepad()
                try {
                    delay(BUTTON_PULSE_MS)
                } finally {
                    if (hid.buttonUp(b)) flushGamepad()
                }
                delay(BUTTON_GAP_MS)
            }
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
        track(scope.launch {
            // A key still down from a previous tap would swallow this press: wait for it to come up.
            var waited = 0L
            while (keys.any { hid.isKeyDown(it) } && waited < MAX_TAP_WAIT_MS) {
                delay(10)
                waited += 10
            }
            keysDown(keys)
            try {
                delay(holdMs)
            } finally {
                keysUp(keys)
            }
        })
    }

    private fun runMacro(macro: Action.Macro) {
        track(scope.launch {
            for (step in macro.steps) {
                if (step.keys.isNotEmpty()) {
                    keysDown(step.keys)
                    try {
                        delay(step.holdMs.coerceAtLeast(MIN_MACRO_HOLD_MS).toLong())
                    } finally {
                        keysUp(step.keys)
                    }
                }
                if (step.delayMs > 0) delay(step.delayMs.toLong())
            }
        })
    }

    private fun track(job: Job) {
        tapJobs.removeAll { it.isCompleted }
        tapJobs += job
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
        const val BUTTON_GAP_MS = 40L
        const val MIN_MACRO_HOLD_MS = 20
        const val MAX_TAP_WAIT_MS = 200L

        /** The value a control rests at before the user touches it. */
        fun restValue(control: Control): Float = 0f

        /** True when [value] is the "let go" of a held control (momentary up, slider back to rest). */
        fun isRelease(control: Control, value: Float): Boolean = when (control.type) {
            ControlType.MOMENTARY -> value < 0.5f
            ControlType.SLIDER -> value == restValue(control)
            else -> false
        }

        /**
         * A slider's range is the control's range, whatever its axis action says, so the two can
         * never disagree and leave half the travel dead (A7, A25). Other controls use the action's.
         */
        fun isUnipolar(control: Control, action: Action.VJoyAxis): Boolean =
            if (control.type == ControlType.SLIDER) control.range == AxisRange.UNIPOLAR
            else action.range == AxisRange.UNIPOLAR

        /** True when every action on [control] can be produced by the "GameCtl Pad" HID device. */
        fun hidCapable(control: Control): Boolean {
            val actions = when (control.type) {
                ControlType.SWITCH, ControlType.RADIO -> control.positions.map { it.action }
                ControlType.ROTARY -> listOf(control.cw, control.ccw)
                ControlType.JOYSTICK -> listOf(control.action, control.actionY)
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
