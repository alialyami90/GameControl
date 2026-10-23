package com.gamecontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.FeedbackSource
import com.gamecontroller.model.IndicatorState
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.RaceMotion
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.lit
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws any control from the profile and wires its input. [interactive] is false in the layout
 * editor, where the same faces are shown but touches belong to the editor.
 */
@Composable
fun ControlView(
    control: Control,
    value: Float,
    indicator: IndicatorState?,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    valueY: Float = 0f,
    onValueY: (Float) -> Unit = {},
) {
    when (control.type) {
        ControlType.MOMENTARY -> MomentaryButton(control, indicator, enabled && interactive, enabled, onValue, modifier)
        ControlType.LATCHING -> LatchingButton(control, value, indicator, enabled && interactive, enabled, onValue, modifier)
        ControlType.SWITCH -> SelectorSwitch(control, value, indicator, enabled && interactive, enabled, onValue, modifier)
        ControlType.RADIO -> RadioKeys(control, value, indicator, enabled && interactive, enabled, onValue, modifier)
        ControlType.SLIDER -> AxisSlider(control, value, enabled && interactive, enabled, onValue, modifier)
        // A joystick's value is its left-right axis; [valueY] / [onValueY] carry forward-back.
        ControlType.JOYSTICK -> PilotJoystick(control, value, valueY, enabled && interactive, enabled, onValue, onValueY, modifier)
        ControlType.ROTARY -> RotaryKnob(control, indicator, enabled && interactive, enabled, onStep, modifier, labelMinHeight = if (interactive) 0.dp else 96.dp)
    }
}

/**
 * Fires on finger down and finger up, so momentary controls feel like real switches.
 * [onCancel] runs instead of [onUp] when the gesture is taken away (another handler consumed
 * it, the control turned inactive, or it left composition), so a held key is always released.
 */
private fun Modifier.pressAndRelease(
    active: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit = onUp,
): Modifier =
    if (!active) this else pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            onDown()
            var released = false
            try {
                released = waitForUpOrCancellation() != null
            } finally {
                if (released) onUp() else onCancel()
            }
        }
    }

@Composable
private fun MomentaryButton(
    control: Control,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberPressHaptic()
    val currentOnValue by rememberUpdatedState(onValue)
    val accent = control.accent.lit()
    val lit = pressed || indicator?.lit == true
    fun release() {
        if (pressed) { pressed = false; currentOnValue(0f) }
    }
    // Leaving composition mid-press (page switch, visibleWhen, profile reload) must not leave the key down.
    DisposableEffect(Unit) { onDispose { release() } }
    ControlCap(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = control.label
                if (!enabled) stateDescription = "Not available on this vehicle"
            }
            .pressAndRelease(
                active,
                onDown = { pressed = true; haptic(); currentOnValue(1f) },
                onUp = { release() },
            ),
        lit = lit,
        accent = accent,
        source = indicator?.source ?: FeedbackSource.LOCAL,
        pressed = pressed,
        enabled = enabled,
        // A momentary press shows only the wash; the band is for latched state the game reports.
        showBand = indicator?.lit == true,
    ) {
        CapFace(control.label, control.icon, lit, accent, indicator?.text)
    }
}

@Composable
private fun LatchingButton(
    control: Control,
    value: Float,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberPressHaptic()
    val on = value >= 0.5f
    val lit = indicator?.lit ?: on
    val accent = control.accent.lit()
    val currentOn by rememberUpdatedState(on)
    val currentOnValue by rememberUpdatedState(onValue)
    ControlCap(
        modifier = modifier
            .semantics {
                role = Role.Switch
                contentDescription = control.label
                stateDescription = when {
                    !enabled -> "Not available on this vehicle"
                    indicator?.source == FeedbackSource.PENDING -> "Waiting for the game"
                    lit -> "On"
                    else -> "Off"
                }
            }
            .pressAndRelease(
                active,
                onDown = { pressed = true },
                onUp = {
                    pressed = false
                    haptic()
                    currentOnValue(if (currentOn) 0f else 1f)
                },
                // A cancelled tap does not toggle.
                onCancel = { pressed = false },
            ),
        lit = lit,
        accent = accent,
        source = indicator?.source ?: FeedbackSource.LOCAL,
        pressed = pressed,
        enabled = enabled,
    ) {
        CapFace(control.label, control.icon, lit, accent, indicator?.text)
    }
}

/** Index of the active position: the game's report wins over the local value. */
private fun activeIndex(control: Control, value: Float, indicator: IndicatorState?): Int {
    val fromGame = indicator?.text?.let { t -> control.positions.indexOfFirst { it.stateValue == t } } ?: -1
    return if (fromGame >= 0) fromGame else value.roundToInt().coerceIn(0, (control.positions.size - 1).coerceAtLeast(0))
}

/**
 * Multi-position selector: one lever in a recessed gate. The thumb slides between positions;
 * tap a position or drag the thumb.
 */
@Composable
private fun SelectorSwitch(
    control: Control,
    value: Float,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier,
) {
    val positions = control.positions.ifEmpty { listOf(com.gamecontroller.model.Position("—")) }
    val n = positions.size
    val index = activeIndex(control, value, indicator)
    val accent = control.accent.lit()
    val haptics = LocalHapticFeedback.current
    val thumb by animateFloatAsState(index.toFloat(), RaceMotion.release(), label = "thumb")
    val currentOnValue by rememberUpdatedState(onValue)
    val currentIndex by rememberUpdatedState(index)
    var box by remember { mutableStateOf(IntSize.Zero) }
    val horizontal = box.width >= box.height
    val pending = indicator?.source == FeedbackSource.PENDING

    fun pick(i: Int) {
        if (i != currentIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnValue(i.toFloat())
        }
    }
    fun indexAt(o: Offset): Int {
        val f = if (horizontal) o.x / box.width.coerceAtLeast(1) else o.y / box.height.coerceAtLeast(1)
        return (f * n).toInt().coerceIn(0, n - 1)
    }

    Box(
        modifier
            .onSizeChanged { box = it }
            .clip(CapShape())
            .background(Race.Panel)
            .border(1.dp, Race.Line, CapShape())
            .semantics {
                role = Role.DropdownList
                contentDescription = control.label
                stateDescription = if (!enabled) "Not available on this vehicle" else positions[index].label
            }
            .then(
                if (!active) Modifier else Modifier
                    .pointerInput(n, horizontal) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            pick(indexAt(down.position))
                            var change = down
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == change.id } ?: break
                                if (!c.pressed) break
                                pick(indexAt(c.position)); c.consume(); change = c
                            }
                        }
                    }
            )
            .padding(4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val segW = if (horizontal) maxWidth / n else maxWidth
            val segH = if (horizontal) maxHeight else maxHeight / n
            // Thumb: a raised cap riding in the gate.
            ControlCap(
                modifier = Modifier
                    .offset(x = if (horizontal) segW * thumb else 0.dp, y = if (horizontal) 0.dp else segH * thumb)
                    .size(segW, segH)
                    .padding(2.dp),
                lit = enabled,
                accent = accent,
                source = indicator?.source ?: FeedbackSource.LOCAL,
                enabled = enabled,
                cut = 8.dp,
                showBand = !pending,
            ) {}
            val content: @Composable (Int) -> Unit = { i ->
                val selected = i == index
                val ink by animateColorAsState(if (selected && enabled) accent else Race.InkMuted, RaceMotion.settle(), label = "seg")
                Box(
                    Modifier
                        .size(segW, segH)
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    val p = positions[i]
                    val vector = IconLibrary[p.icon]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vector != null) Icon(vector, null, tint = ink, modifier = Modifier.size(22.dp))
                        Text(
                            p.label.uppercase(),
                            style = if (segH >= 56.dp && segW >= 56.dp) RaceType.readout else RaceType.capLabel,
                            color = if (selected && enabled) Race.Ink else ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (horizontal) Row { repeat(n) { content(it) } } else Column { repeat(n) { content(it) } }
        }
    }
}

/** Separate keys, one lit at a time, each with its own lamp. */
@Composable
private fun RadioKeys(
    control: Control,
    value: Float,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier,
) {
    val positions = control.positions
    val index = activeIndex(control, value, indicator)
    val accent = control.accent.lit()
    val haptic = rememberPressHaptic()
    val currentOnValue by rememberUpdatedState(onValue)
    BoxWithConstraints(modifier) {
        val horizontal = maxWidth >= maxHeight
        val n = positions.size.coerceAtLeast(1)
        val gap = 6.dp
        val keyW = if (horizontal) (maxWidth - gap * (n - 1)) / n else maxWidth
        val keyH = if (horizontal) maxHeight else (maxHeight - gap * (n - 1)) / n
        val keys: @Composable () -> Unit = {
            positions.forEachIndexed { i, p ->
                var pressed by remember { mutableStateOf(false) }
                val selected = i == index
                ControlCap(
                    modifier = Modifier
                        .size(keyW, keyH)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "${control.label}: ${p.label}"
                            this.selected = selected
                        }
                        .pressAndRelease(
                            active,
                            onDown = { pressed = true },
                            onUp = {
                                pressed = false
                                haptic()
                                currentOnValue(i.toFloat())
                            },
                            onCancel = { pressed = false },
                        ),
                    lit = selected,
                    accent = accent,
                    source = if (selected) indicator?.source ?: FeedbackSource.LOCAL else FeedbackSource.LOCAL,
                    pressed = pressed,
                    enabled = enabled,
                ) {
                    CapFace(p.label, p.icon, selected, accent)
                }
            }
        }
        if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(gap)) { keys() }
        else Column(verticalArrangement = Arrangement.spacedBy(gap)) { keys() }
    }
}

/**
 * Axis slider in a recessed slot. Bipolar sliders rest at the centre detent; spring-return
 * sliders snap back to rest on release. The accent fill always grows out from the rest point.
 */
@Composable
private fun AxisSlider(
    control: Control,
    value: Float,
    active: Boolean,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    modifier: Modifier,
) {
    val bipolar = control.range == AxisRange.BIPOLAR
    val min = if (bipolar) -1f else 0f
    val rest = 0f
    val accent = control.accent.lit()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val shown = remember { Animatable(value) }
    // Follow outside changes (game echo, profile reload) unless the finger owns the thumb or a
    // spring-back is already heading there. A drag leaves shown == value, so no re-sync is needed after it.
    LaunchedEffect(value) {
        if (!dragging && shown.value != value && shown.targetValue != value) shown.animateTo(value, RaceMotion.settle())
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val vertical = control.vertical
    val thumbPx = with(LocalDensity.current) { 34.dp.toPx() }
    val currentOnValue by rememberUpdatedState(onValue)

    fun valueAt(o: Offset): Float {
        val len = (if (vertical) box.height else box.width) - thumbPx
        val pos = ((if (vertical) box.height - o.y else o.x) - thumbPx / 2) / len.coerceAtLeast(1f)
        return (min + pos.coerceIn(0f, 1f) * (1f - min))
    }

    val pct = ((shown.value) * 100).roundToInt()
    Column(
        modifier.semantics {
            contentDescription = control.label
            stateDescription = if (!enabled) "Not available on this vehicle" else "$pct percent"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val readoutText = (if (bipolar && pct > 0) "+" else "") + pct
        val readoutInk = if (abs(shown.value - rest) > 0.01f) accent else Race.InkFaint
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 104.dp) {
                // One-column sliders: label on up to two compact lines above the readout, never "THROT...".
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        control.label.uppercase(),
                        style = RaceType.unit.copy(lineHeight = 12.sp, letterSpacing = 0.04.em),
                        color = Race.InkMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(readoutText, style = RaceType.readout, color = readoutInk)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        control.label.uppercase(),
                        style = RaceType.capLabel,
                        color = Race.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(readoutText, style = RaceType.readout, color = readoutInk)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { box = it }
                .clip(CapShape())
                .background(Race.Panel)
                .border(1.dp, if (enabled) Race.Line else Race.LineStrong, CapShape())
                .then(
                    if (!active) Modifier else Modifier.pointerInput(vertical, bipolar, control.springReturn) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragging = true
                            var last = valueAt(down.position)
                            scope.launch { shown.snapTo(last) }
                            currentOnValue(last)
                            // finally: a cancelled drag (page switch, slider removed) still springs back.
                            try { while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                val v = valueAt(c.position)
                                // Detent tick when crossing the rest point on a bipolar axis.
                                if (bipolar && (last < 0f) != (v < 0f)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                last = v
                                scope.launch { shown.snapTo(v) }
                                currentOnValue(v)
                                c.consume()
                            } } finally {
                                dragging = false
                                if (control.springReturn) {
                                    currentOnValue(rest)
                                    scope.launch { shown.animateTo(rest, RaceMotion.springBack()) }
                                }
                            }
                        }
                    }
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val len = (if (vertical) size.height else size.width) - thumbPx
                fun at(v: Float) = thumbPx / 2 + (v - min) / (1f - min) * len
                val cross = if (vertical) size.width else size.height
                // Spring-centred bipolar axes draw as a hydraulic lever: a ball knob in a wide gate
                // with a centre detent notch, the way a machine's valve lever sits.
                val lever = bipolar && control.springReturn
                // Tick ladder.
                val ticks = if (bipolar) 20 else 10
                for (i in 0..ticks) {
                    val v = min + i.toFloat() / ticks * (1f - min)
                    val major = i % 5 == 0
                    val p = at(v)
                    val tl = if (major) cross * 0.22f else cross * 0.12f
                    val c = Race.LineStrong.copy(alpha = if (major) 1f else 0.6f)
                    if (vertical) {
                        val y = size.height - p
                        drawLine(c, Offset(6.dp.toPx(), y), Offset(6.dp.toPx() + tl, y), 1.dp.toPx())
                        drawLine(c, Offset(size.width - 6.dp.toPx() - tl, y), Offset(size.width - 6.dp.toPx(), y), 1.dp.toPx())
                    } else {
                        drawLine(c, Offset(p, 6.dp.toPx()), Offset(p, 6.dp.toPx() + tl), 1.dp.toPx())
                        drawLine(c, Offset(p, size.height - 6.dp.toPx() - tl), Offset(p, size.height - 6.dp.toPx()), 1.dp.toPx())
                    }
                }
                // Fill from rest to value.
                val a = at(rest); val b = at(shown.value)
                val lo = minOf(a, b); val hi = maxOf(a, b)
                val slot = (if (lever) 10.dp else 6.dp).toPx()
                if (lever) {
                    // Detent notches either side of the gate at the rest point.
                    val p = at(rest)
                    val n = 6.dp.toPx()
                    val gap = slot / 2 + 3.dp.toPx()
                    val mid = cross / 2
                    val notch = Race.Ink.copy(alpha = 0.7f)
                    fun tri(a: Offset, b: Offset, t: Offset) = androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(t.x, t.y); close() }
                    if (vertical) {
                        val yy = size.height - p
                        drawPath(tri(Offset(mid - gap - n, yy - n / 1.4f), Offset(mid - gap - n, yy + n / 1.4f), Offset(mid - gap, yy)), notch)
                        drawPath(tri(Offset(mid + gap + n, yy - n / 1.4f), Offset(mid + gap + n, yy + n / 1.4f), Offset(mid + gap, yy)), notch)
                    } else {
                        drawPath(tri(Offset(p - n / 1.4f, mid - gap - n), Offset(p + n / 1.4f, mid - gap - n), Offset(p, mid - gap)), notch)
                        drawPath(tri(Offset(p - n / 1.4f, mid + gap + n), Offset(p + n / 1.4f, mid + gap + n), Offset(p, mid + gap)), notch)
                    }
                }
                if (vertical) {
                    val x = size.width / 2 - slot / 2
                    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(x, thumbPx / 2), Size(slot, len), CornerRadius(slot / 2))
                    if (enabled) drawRoundRect(accent, Offset(x, size.height - hi), Size(slot, hi - lo), CornerRadius(slot / 2))
                } else {
                    val y = size.height / 2 - slot / 2
                    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(thumbPx / 2, y), Size(len, slot), CornerRadius(slot / 2))
                    if (enabled) drawRoundRect(accent, Offset(lo, y), Size(hi - lo, slot), CornerRadius(slot / 2))
                }
                val t = at(shown.value)
                if (lever) {
                    // Ball knob on the lever.
                    val kc = if (vertical) Offset(size.width / 2, size.height - t) else Offset(t, size.height / 2)
                    val kr = minOf(thumbPx / 2, cross / 2 - 4.dp.toPx())
                    drawCircle(Color.Black.copy(alpha = 0.5f), kr, kc + Offset(0f, 3.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Race.CapHigh, Race.Cap), kc.y - kr, kc.y + kr), kr, kc)
                    drawCircle(if (dragging) accent else Race.LineStrong, kr, kc, style = Stroke(2.dp.toPx()))
                    drawCircle(if (enabled) accent else Race.InkFaint, kr * 0.3f, kc)
                    return@Canvas
                }
                // T-handle thumb.
                val handleCross = cross - 12.dp.toPx()
                val handleLen = thumbPx - 6.dp.toPx()
                val tl = if (vertical) Offset(6.dp.toPx(), size.height - t - handleLen / 2) else Offset(t - handleLen / 2, 6.dp.toPx())
                val hs = if (vertical) Size(handleCross, handleLen) else Size(handleLen, handleCross)
                drawRoundRect(Color.Black.copy(alpha = 0.5f), tl + Offset(0f, 3.dp.toPx()), hs, CornerRadius(6.dp.toPx()))
                drawRoundRect(Brush.verticalGradient(listOf(Race.CapHigh, Race.Cap), startY = tl.y, endY = tl.y + hs.height), tl, hs, CornerRadius(6.dp.toPx()))
                drawRoundRect(if (dragging) accent else Race.LineStrong, tl, hs, CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                // Grip line in the handle's centre.
                val gripC = if (enabled) accent else Race.InkFaint
                if (vertical) {
                    val y = tl.y + hs.height / 2
                    drawLine(gripC, Offset(tl.x + hs.width * 0.2f, y), Offset(tl.x + hs.width * 0.8f, y), 3.dp.toPx(), StrokeCap.Round)
                } else {
                    val x = tl.x + hs.width / 2
                    drawLine(gripC, Offset(x, tl.y + hs.height * 0.2f), Offset(x, tl.y + hs.height * 0.8f), 3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * Encoder knob: drag around it to send steps (one per 15 degrees, with a haptic tick), or tap
 * the left or right half for a single step.
 */
@Composable
private fun RotaryKnob(
    control: Control,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier,
    labelMinHeight: androidx.compose.ui.unit.Dp,
) {
    val accent = control.accent.lit()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var angle by remember { mutableFloatStateOf(0f) }
    var steps by remember { mutableIntStateOf(0) }
    var turning by remember { mutableStateOf(false) }
    val shownAngle by animateFloatAsState(angle, RaceMotion.settle(), label = "knob")
    val currentOnStep by rememberUpdatedState(onStep)
    val stepDeg = 15f
    var showLabel by remember { mutableStateOf(true) }

    fun step(d: Int) {
        steps += d
        angle += d * stepDeg
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnStep(d)
    }

    Column(
        modifier
            // The play surface always labels a knob; the editor's compact grid drops it like CapFace does.
            .onSizeChanged { showLabel = with(density) { it.height.toDp() } >= labelMinHeight }
            .semantics {
                contentDescription = control.label
                if (!enabled) stateDescription = "Not available on this vehicle"
                onClick(label = "Step clockwise") { step(1); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            Box(
                Modifier
                    .size(d)
                    .then(
                        if (!active) Modifier else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val c0 = Offset(size.width / 2f, size.height / 2f)
                                var prev = atan2(down.position.y - c0.y, down.position.x - c0.x)
                                var acc = 0f
                                var moved = false
                                turning = true
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    val now = atan2(c.position.y - c0.y, c.position.x - c0.x)
                                    var delta = (now - prev) * 180f / PI.toFloat()
                                    if (delta > 180f) delta -= 360f
                                    if (delta < -180f) delta += 360f
                                    prev = now
                                    acc += delta
                                    while (acc >= stepDeg) { step(1); acc -= stepDeg; moved = true }
                                    while (acc <= -stepDeg) { step(-1); acc += stepDeg; moved = true }
                                    c.consume()
                                }
                                turning = false
                                if (!moved) step(if (down.position.x >= size.width / 2f) 1 else -1)
                            }
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    val c = center
                    val ring = (r * 0.18f).coerceIn(6.dp.toPx(), 14.dp.toPx())
                    // Tick ring.
                    for (i in 0 until 24) {
                        val a = (i * stepDeg - 90f) * PI.toFloat() / 180f
                        val major = i % 6 == 0
                        val r1 = r - 1.dp.toPx(); val r0 = r1 - (if (major) ring * 0.7f else ring * 0.4f)
                        drawLine(
                            if (major) Race.LineStrong else Race.Line,
                            Offset(c.x + cos(a) * r0, c.y + sin(a) * r0),
                            Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                            (if (major) 2.dp else 1.dp).toPx(), StrokeCap.Round,
                        )
                    }
                    val kr = r - ring - 2.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.5f), kr, c + Offset(0f, 4.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Race.CapHigh, Race.Cap), startY = c.y - kr, endY = c.y + kr), kr, c)
                    drawCircle(if (turning) accent else Race.Line, kr, c, style = Stroke(1.5.dp.toPx()))
                    // Knurled grip ring.
                    for (i in 0 until 36) {
                        val a = (i * 10f + shownAngle) * PI.toFloat() / 180f
                        drawLine(
                            Color.Black.copy(alpha = 0.28f),
                            Offset(c.x + cos(a) * (kr - 7.dp.toPx()), c.y + sin(a) * (kr - 7.dp.toPx())),
                            Offset(c.x + cos(a) * (kr - 1.dp.toPx()), c.y + sin(a) * (kr - 1.dp.toPx())),
                            1.dp.toPx(),
                        )
                    }
                    // Pointer.
                    val pa = (shownAngle - 90f) * PI.toFloat() / 180f
                    drawLine(
                        if (enabled) accent else Race.InkFaint,
                        Offset(c.x + cos(pa) * kr * 0.25f, c.y + sin(pa) * kr * 0.25f),
                        Offset(c.x + cos(pa) * (kr - 10.dp.toPx()), c.y + sin(pa) * (kr - 10.dp.toPx())),
                        4.dp.toPx(), StrokeCap.Round,
                    )
                }
                val vector = IconLibrary[control.icon]
                val readout = indicator?.text
                if (readout != null) {
                    Text(readout, style = RaceType.readout, color = Race.Ink)
                } else if (vector != null && d >= 110.dp) {
                    Icon(vector, null, tint = Race.InkMuted, modifier = Modifier.size(d * 0.16f).offset(y = d * 0.12f))
                }
            }
        }
        if (control.label.isNotBlank() && showLabel) {
            Text(
                control.label.uppercase(),
                style = RaceType.capLabel,
                color = Race.InkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
