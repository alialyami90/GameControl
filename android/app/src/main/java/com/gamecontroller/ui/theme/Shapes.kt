package com.gamecontroller.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Horizontal lean of slanted panels, as run over rise. About 12 degrees. */
const val SlantRatio = 0.21f

/**
 * Racing-menu parallelogram: both sides lean forward. Used for tabs, chips, the gear box and
 * title plates. [lean] overrides the run in dp; by default it follows the height.
 */
class SlantShape(private val lean: Dp? = null) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val run = lean?.let { with(density) { it.toPx() } } ?: (size.height * SlantRatio)
        return Outline.Generic(Path().apply {
            moveTo(run, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - run, size.height)
            lineTo(0f, size.height)
            close()
        })
    }
}

/**
 * Control cap: square-shouldered with the top-right corner cut, like a livery number panel.
 * Other corners get a small radius so the cap still reads as something you press.
 */
class CapShape(private val cut: Dp = 14.dp, private val radius: Dp = 6.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(size.minDimension / 3)
        val r = with(density) { radius.toPx() }.coerceAtMost(size.minDimension / 4)
        return Outline.Generic(Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - r)
            quadraticBezierTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            close()
        })
    }
}
