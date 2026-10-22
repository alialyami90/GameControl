package com.gamecontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.FeedbackSource
import com.gamecontroller.ui.theme.Rig
import com.gamecontroller.ui.theme.RigMotion

/** Height of the hi-vis chevron band that marks a control as on. */
val LatchBandHeight = 7.dp
val CapCorner = 10.dp

/**
 * The raised rubber cap every play control is built on. When [lit], a hi-vis chevron band
 * slides across the top edge: solid chevrons once the game confirms ([FeedbackSource.GAME] or
 * a local-only latch), marching hatch while a press is waiting for the game to echo it.
 */
@Composable
fun ControlCap(
    modifier: Modifier = Modifier,
    lit: Boolean,
    accent: Color,
    source: FeedbackSource = FeedbackSource.LOCAL,
    pressed: Boolean = false,
    enabled: Boolean = true,
    corner: Dp = CapCorner,
    showBand: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val band by animateFloatAsState(if (lit && showBand) 1f else 0f, RigMotion.latch(), label = "band")
    val wash by animateFloatAsState(if (lit) 1f else 0f, RigMotion.settle(), label = "wash")
    val travel by animateFloatAsState(if (pressed) 0.965f else 1f, if (pressed) RigMotion.press() else RigMotion.release(), label = "travel")
    val elevation by animateDpAsState(if (pressed || !enabled) 0.5.dp else 5.dp, RigMotion.settle(), label = "elev")
    val border by animateColorAsState(
        if (lit) accent.copy(alpha = 0.85f) else Rig.Stitch,
        RigMotion.settle(), label = "border",
    )
    val pending = lit && source == FeedbackSource.PENDING
    val phase = if (pending) {
        val t = rememberInfiniteTransition(label = "hatch")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing)), label = "phase").value
    } else 0f

    Box(
        modifier
            .scale(travel)
            .alpha(if (enabled) 1f else 0.42f)
            .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .drawWithContent {
                val r = CornerRadius(corner.toPx())
                if (pressed) {
                    drawRect(Rig.CapPressed)
                } else {
                    drawRect(Brush.verticalGradient(listOf(Rig.CapHigh, Rig.Cap), endY = size.height * 0.9f))
                }
                if (wash > 0f) drawRect(accent.copy(alpha = 0.13f * wash))
                // Moulded highlight along the top lip.
                drawRect(Color.White.copy(alpha = if (pressed) 0.02f else 0.07f), size = Size(size.width, 1.dp.toPx()))
                drawContent()
                if (band > 0f) drawLatchBand(accent, band, pending, phase)
                val sw = (if (lit) 1.5.dp else 1.dp).toPx()
                drawRoundRect(
                    color = if (enabled) border else Rig.StitchStrong,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(r.x - sw / 2),
                    style = Stroke(
                        width = sw,
                        pathEffect = if (enabled) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            },
        content = content,
    )
}

private fun DrawScope.drawLatchBand(accent: Color, progress: Float, pending: Boolean, phase: Float) {
    val h = LatchBandHeight.toPx()
    val w = size.width * progress
    clipRect(0f, 0f, w, h) {
        if (pending) {
            // Marching diagonal hatch: sent, waiting for the game.
            drawRect(accent.copy(alpha = 0.22f))
            val step = 9.dp.toPx()
            var x = -h * 2 + phase * step
            while (x < w + h) {
                val p = Path().apply {
                    moveTo(x, h); lineTo(x + h, 0f); lineTo(x + h + step / 2, 0f); lineTo(x + step / 2, h); close()
                }
                drawPath(p, accent)
                x += step
            }
        } else {
            drawRect(accent)
            // Chevron tape: dark arrowheads pointing along the band.
            val step = 11.dp.toPx()
            val ink = Color.Black.copy(alpha = 0.32f)
            var x = step / 2
            while (x < w) {
                val p = Path().apply {
                    moveTo(x - h * 0.35f, 0f); lineTo(x + h * 0.15f, h / 2); lineTo(x - h * 0.35f, h)
                    lineTo(x - h * 0.05f, h); lineTo(x + h * 0.45f, h / 2); lineTo(x - h * 0.05f, 0f); close()
                }
                drawPath(p, ink)
                x += step
            }
        }
    }
}
