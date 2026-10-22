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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.FeedbackSource
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.RaceMotion

/** Height of the racing stripe that marks a control as on. */
val LatchBandHeight = 7.dp
val CapCorner = 6.dp

/**
 * The raised cap every play control is built on, with its top-right corner cut. When [lit], a
 * racing stripe sweeps across the top edge: solid with speed slashes once the game confirms
 * ([FeedbackSource.GAME] or a local-only latch), marching slashes while a press is waiting
 * for the game to echo it.
 */
@Composable
fun ControlCap(
    modifier: Modifier = Modifier,
    lit: Boolean,
    accent: Color,
    source: FeedbackSource = FeedbackSource.LOCAL,
    pressed: Boolean = false,
    enabled: Boolean = true,
    cut: Dp = 14.dp,
    showBand: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = CapShape(cut = cut, radius = CapCorner)
    val band by animateFloatAsState(if (lit && showBand) 1f else 0f, RaceMotion.latch(), label = "band")
    val wash by animateFloatAsState(if (lit) 1f else 0f, RaceMotion.settle(), label = "wash")
    val travel by animateFloatAsState(if (pressed) 0.965f else 1f, if (pressed) RaceMotion.press() else RaceMotion.release(), label = "travel")
    val elevation by animateDpAsState(if (pressed || !enabled) 0.5.dp else 5.dp, RaceMotion.settle(), label = "elev")
    val border by animateColorAsState(
        if (lit) accent.copy(alpha = 0.9f) else Race.Line,
        RaceMotion.settle(), label = "border",
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
                if (pressed) {
                    drawRect(Race.CapPressed)
                } else {
                    drawRect(Brush.verticalGradient(listOf(Race.CapHigh, Race.Cap), endY = size.height * 0.9f))
                }
                if (wash > 0f) drawRect(accent.copy(alpha = 0.14f * wash))
                // Bevel highlight along the top edge.
                drawRect(Color.White.copy(alpha = if (pressed) 0.02f else 0.08f), size = Size(size.width, 1.dp.toPx()))
                drawContent()
                if (band > 0f) drawLatchBand(accent, band, pending, phase)
                val sw = (if (lit) 1.5.dp else 1.dp).toPx()
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline,
                    color = if (enabled) border else Race.LineStrong,
                    style = Stroke(
                        width = sw * 2,
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
    val lean = h * 1.2f
    val step = 10.dp.toPx()
    clipRect(0f, 0f, w, h) {
        if (pending) {
            // Hollow band with sparse marching slashes: sent, waiting for the game. Unlike the solid
            // confirmed stripe it still reads as "not yet" in a still frame (animations off).
            val sw = 1.5.dp.toPx()
            drawRect(accent, topLeft = Offset(sw / 2, sw / 2), size = Size(w - sw, h - sw), style = Stroke(sw))
            var x = -lean - step * 2 + phase * step * 2
            while (x < w + lean) {
                drawPath(slash(x, h, lean, step * 0.35f), accent)
                x += step * 2
            }
        } else {
            drawRect(accent)
            // Speed slashes leaning forward, cut into the stripe's leading half.
            val ink = Color.Black.copy(alpha = 0.3f)
            var x = w * 0.55f
            var gap = 3.dp.toPx()
            while (x < w + lean) {
                drawPath(slash(x, h, lean, gap), ink)
                x += gap + step * 0.6f
                gap += 1.dp.toPx()
            }
        }
    }
}

private fun slash(x: Float, h: Float, lean: Float, width: Float) = Path().apply {
    moveTo(x + lean, 0f); lineTo(x + lean + width, 0f); lineTo(x + width, h); lineTo(x, h); close()
}
