package com.gamecontroller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.Control
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceMotion
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.lit
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Inside this share of the throw the stick reads as centred, like a real pilot valve. */
private const val DeadZone = 0.08f

/**
 * Pilot joystick from a machine cab: a round gate with a chunky knob that springs back to
 * centre. Left-right is [x], forward-back is [y] (forward +1). A dead-zone ring marks centre,
 * and leaving it gives one haptic tick. The plate carries a caution band in the control's
 * accent that lights while the stick is off centre.
 */
@Composable
internal fun PilotJoystick(
    control: Control,
    x: Float,
    y: Float,
    active: Boolean,
    enabled: Boolean,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    modifier: Modifier,
) {
    val accent = control.accent.lit()
    val haptic = rememberPressHaptic()
    val scope = rememberCoroutineScope()
    val sx = remember { Animatable(x) }
    val sy = remember { Animatable(y) }
    var held by remember { mutableStateOf(false) }
    val currentOnX by rememberUpdatedState(onX)
    val currentOnY by rememberUpdatedState(onY)

    fun release() {
        if (!held) return
        held = false
        currentOnX(0f); currentOnY(0f)
        scope.launch { sx.animateTo(0f, RaceMotion.springBack()) }
        scope.launch { sy.animateTo(0f, RaceMotion.springBack()) }
    }
    DisposableEffect(Unit) { onDispose { release() } }

    val off = abs(sx.value) > DeadZone || abs(sy.value) > DeadZone
    val shape = CapShape()
    Column(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(Race.Panel)
                drawCautionBand(accent, lit = off && enabled)
                drawOutline(shape.createOutline(size, layoutDirection, this), if (enabled) Race.Line else Race.LineStrong, style = Stroke(
                    1.dp.toPx(),
                    pathEffect = if (enabled) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ))
            }
            .semantics {
                contentDescription = control.label
                stateDescription = if (!enabled) "Not available on this vehicle"
                else "Left-right ${(sx.value * 100).roundToInt()} percent, forward-back ${(sy.value * 100).roundToInt()} percent"
            }
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconLibrary[control.icon]?.let {
                Icon(it, contentDescription = null, tint = if (off) accent else Race.InkMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                control.label.uppercase(),
                style = RaceType.capLabel,
                color = if (off) Race.Ink else Race.InkMuted,
                // Two-part names (BLADE LIFT / TILT) wrap rather than truncate.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            Box(
                Modifier
                    .size(d)
                    .then(
                        if (!active) Modifier else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val r = size.width / 2f
                                val throwPx = r * 0.68f
                                held = true
                                var wasOff = false
                                fun moveTo(p: Offset) {
                                    var vx = (p.x - r) / throwPx
                                    var vy = -(p.y - r) / throwPx
                                    val m = hypot(vx, vy)
                                    if (m > 1f) { vx /= m; vy /= m }
                                    val outside = hypot(vx, vy) > DeadZone
                                    if (outside && !wasOff) haptic()
                                    wasOff = outside
                                    val ox = if (outside) vx else 0f
                                    val oy = if (outside) vy else 0f
                                    scope.launch { sx.snapTo(vx) }
                                    scope.launch { sy.snapTo(vy) }
                                    currentOnX(ox); currentOnY(oy)
                                }
                                // finally: a cancelled drag (page switch, control removed) still centres the stick.
                                try {
                                    moveTo(down.position)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        moveTo(c.position)
                                        c.consume()
                                    }
                                } finally {
                                    release()
                                }
                            }
                        }
                    ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val gateR = r * 0.94f
                    val knobR = r * 0.26f
                    val throwPx = r * 0.68f
                    // Gate: a recessed round well with a seam and a tick ring.
                    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Race.Ground), c, gateR), gateR, c)
                    drawCircle(if (enabled) Race.LineStrong else Race.Line, gateR, c, style = Stroke(1.5.dp.toPx()))
                    for (i in 0 until 24) {
                        val a = Math.toRadians(i * 15.0)
                        val major = i % 6 == 0
                        val r1 = gateR - (if (major) 9.dp else 5.dp).toPx()
                        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        drawLine(Race.LineStrong.copy(alpha = if (major) 1f else 0.55f), c + dir * r1, c + dir * (gateR - 2.dp.toPx()), 1.dp.toPx())
                    }
                    // Cross guides and the dead-zone ring.
                    val g = Race.Line
                    drawLine(g, Offset(c.x - throwPx, c.y), Offset(c.x + throwPx, c.y), 1.dp.toPx())
                    drawLine(g, Offset(c.x, c.y - throwPx), Offset(c.x, c.y + throwPx), 1.dp.toPx())
                    drawCircle(
                        Race.LineStrong, throwPx * DeadZone + knobR * 0.35f, c,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                    )
                    val k = c + Offset(sx.value, -sy.value) * throwPx
                    // Accent rod from centre to knob while off centre, so the throw reads at a glance.
                    if (enabled && hypot(sx.value, sy.value) > DeadZone) {
                        drawLine(accent, c, k, 4.dp.toPx(), StrokeCap.Round)
                    }
                    // Chunky knob: shadow, raised gradient, rim, grip dimple.
                    drawCircle(Color.Black.copy(alpha = 0.5f), knobR, k + Offset(0f, 4.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Race.CapHigh, Race.Cap), k.y - knobR, k.y + knobR), knobR, k)
                    drawCircle(if (held) accent else Race.LineStrong, knobR, k, style = Stroke(2.dp.toPx()))
                    drawCircle(if (enabled) accent else Race.InkFaint, knobR * 0.28f, k)
                }
            }
        }
    }
}

/** Diagonal caution band along the plate's foot: faint at rest, full accent while in use. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCautionBand(accent: Color, lit: Boolean) {
    val h = 6.dp.toPx()
    val top = size.height - h
    val step = 12.dp.toPx()
    clipRect(0f, top, size.width, size.height) {
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, top), Size(size.width, h))
        var xPos = -h
        while (xPos < size.width + h) {
            drawPath(Path().apply {
                moveTo(xPos + h, top); lineTo(xPos + h + step / 2, top); lineTo(xPos + step / 2, size.height); lineTo(xPos, size.height); close()
            }, accent.copy(alpha = if (lit) 1f else 0.35f))
            xPos += step
        }
    }
}
