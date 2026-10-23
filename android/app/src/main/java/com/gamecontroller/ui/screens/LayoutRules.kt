package com.gamecontroller.ui.screens

import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.Page

/**
 * Smallest span (columns to rows) at which a control still gives every target about 48dp on a
 * phone: one cell per switch or radio position along its long axis, two cells along a slider,
 * and 2x2 for a knob. Capped to the page so a tiny grid never makes the rule unsatisfiable.
 */
fun Control.minSpan(page: Page): Pair<Int, Int> {
    val n = positions.size.coerceAtLeast(2)
    val (w, h) = when (type) {
        // Horizontal only, matching core's sanitize, so what the editor allows survives a reload.
        ControlType.SWITCH, ControlType.RADIO -> n to 1
        ControlType.SLIDER -> if (vertical) 1 to 2 else 2 to 1
        ControlType.ROTARY, ControlType.JOYSTICK -> 2 to 2
        ControlType.MOMENTARY, ControlType.LATCHING -> 1 to 1
    }
    return w.coerceAtMost(page.columns) to h.coerceAtMost(page.rows)
}

/** Width range the editor allows for [c] at its current position. */
fun widthRange(c: Control, page: Page): IntRange {
    val max = (page.columns - c.x).coerceAtLeast(1)
    return c.minSpan(page).first.coerceAtMost(max)..max
}

/** Height range the editor allows for [c] at its current position. */
fun heightRange(c: Control, page: Page): IntRange {
    val max = (page.rows - c.y).coerceAtLeast(1)
    return c.minSpan(page).second.coerceAtMost(max)..max
}
