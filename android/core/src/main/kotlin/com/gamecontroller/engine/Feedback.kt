package com.gamecontroller.engine

import com.gamecontroller.model.Control
import com.gamecontroller.model.Joystick
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.FeedbackSource
import com.gamecontroller.model.IndicatorState
import com.gamecontroller.model.Profile
import kotlin.math.roundToInt

/**
 * Resolves indicator lamps and availability from local control values and game state.
 * Pure functions so the play screen and the tests share one source of truth.
 */
object Feedback {
    /** How long a press waits for the game to echo it before the game's own value wins again. */
    const val PENDING_TIMEOUT_MS = 1500L

    data class Resolved(
        val indicators: Map<String, IndicatorState>,
        val unavailable: Set<String>,
    )

    fun truthy(v: Any?): Boolean? = when (v) {
        null -> null
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty() && v.lowercase() !in setOf("0", "false", "off", "no")
        else -> null
    }

    /** Index of the switch/radio position the game reports, or null when unknown. */
    fun gamePosition(control: Control, game: Map<String, Any>): Int? {
        val key = control.positionKey ?: return null
        val raw = game[key] ?: return null
        val text = when (raw) {
            is Double -> if (raw == raw.toLong().toDouble()) raw.toLong().toString() else raw.toString()
            else -> raw.toString()
        }
        val i = control.positions.indexOfFirst { it.stateValue != null && it.stateValue.equals(text, ignoreCase = true) }
        return i.takeIf { it >= 0 }
    }

    fun isPending(id: String, pending: Map<String, Long>, now: Long): Boolean {
        val t = pending[id] ?: return false
        return now - t < PENDING_TIMEOUT_MS
    }

    fun resolve(
        profile: Profile,
        values: Map<String, Float>,
        game: Map<String, Any>,
        pending: Map<String, Long>,
        now: Long,
    ): Resolved {
        val indicators = HashMap<String, IndicatorState>()
        val unavailable = HashSet<String>()
        for (c in profile.pages.flatMap { it.controls }) {
            c.visibleWhen?.let { key -> if (truthy(game[key]) == false) unavailable += c.id }
            // A joystick's value lives on its two axis ids; it is "on" while either is off centre (R6-5).
            val value = if (c.type == ControlType.JOYSTICK) {
                maxOf(kotlin.math.abs(values[c.id + Joystick.X] ?: 0f), kotlin.math.abs(values[c.id + Joystick.Y] ?: 0f))
            } else values[c.id] ?: 0f
            indicators[c.id] = indicator(c, value, game, isPending(c.id, pending, now))
        }
        return Resolved(indicators, unavailable)
    }

    private fun indicator(c: Control, value: Float, game: Map<String, Any>, pending: Boolean): IndicatorState {
        val gameRaw = c.feedbackKey?.let { game[it] }
        return when (c.type) {
            ControlType.MOMENTARY, ControlType.LATCHING -> {
                val local = value >= 0.5f
                val gameLit = truthy(gameRaw)
                when {
                    gameLit == null -> IndicatorState(local, source = FeedbackSource.LOCAL)
                    pending && gameLit != local -> IndicatorState(local, source = FeedbackSource.PENDING)
                    // A momentary button also lights while it is held.
                    else -> IndicatorState(gameLit || (c.type == ControlType.MOMENTARY && local), text(gameRaw), FeedbackSource.GAME)
                }
            }
            ControlType.SWITCH, ControlType.RADIO -> {
                val fromGame = gamePosition(c, game)
                val idx = if (fromGame != null && !(pending && fromGame != value.toInt())) fromGame else value.toInt()
                val source = when {
                    fromGame == null -> FeedbackSource.LOCAL
                    pending && fromGame != value.toInt() -> FeedbackSource.PENDING
                    else -> FeedbackSource.GAME
                }
                IndicatorState(true, c.positions.getOrNull(idx)?.label, source)
            }
            ControlType.SLIDER, ControlType.ROTARY, ControlType.JOYSTICK -> {
                if (gameRaw != null) IndicatorState(truthy(gameRaw) == true, text(gameRaw), FeedbackSource.GAME)
                else IndicatorState(value != 0f, null, FeedbackSource.LOCAL)
            }
        }
    }

    private fun text(v: Any?): String? = when (v) {
        null, is Boolean -> null
        is Double -> if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(v)
        else -> v.toString()
    }

    /**
     * Brings local values in line with the game for controls the game reports, so the next press
     * of a latching control asks for the opposite of what the game shows. Controls with a pending
     * change keep their local value until the game echoes it or the timeout passes.
     */
    fun syncValues(
        profile: Profile,
        values: Map<String, Float>,
        game: Map<String, Any>,
        pending: Map<String, Long>,
        now: Long,
    ): Map<String, Float> {
        val out = values.toMutableMap()
        for (c in profile.pages.flatMap { it.controls }) {
            if (isPending(c.id, pending, now)) continue
            when (c.type) {
                ControlType.LATCHING -> truthy(c.feedbackKey?.let { game[it] })?.let { out[c.id] = if (it) 1f else 0f }
                ControlType.SWITCH, ControlType.RADIO -> gamePosition(c, game)?.let { out[c.id] = it.toFloat() }
                else -> Unit
            }
        }
        return out
    }

    /** Clears pending entries the game has confirmed or that timed out. */
    fun prunePending(
        profile: Profile,
        values: Map<String, Float>,
        game: Map<String, Any>,
        pending: Map<String, Long>,
        now: Long,
    ): Map<String, Long> = pending.filter { (id, t) ->
        if (now - t >= PENDING_TIMEOUT_MS) return@filter false
        val c = profile.pages.firstNotNullOfOrNull { p -> p.controls.firstOrNull { it.id == id } } ?: return@filter false
        val v = values[id] ?: 0f
        val confirmed = when (c.type) {
            ControlType.LATCHING -> truthy(c.feedbackKey?.let { game[it] }) == (v >= 0.5f)
            ControlType.SWITCH, ControlType.RADIO -> gamePosition(c, game) == v.toInt()
            else -> true
        }
        !confirmed
    }
}
