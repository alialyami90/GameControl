package com.gamecontroller.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring

/**
 * Motion is quick and decisive, like a racing menu: selections snap, latch stripes sweep in.
 * Compose scales these by the system animator setting, so Remove animations cuts them.
 */
object RaceMotion {
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> press(): FiniteAnimationSpec<T> = tween(durationMillis = 70, easing = Standard)
    fun <T> release(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    fun <T> latch(): FiniteAnimationSpec<T> = tween(durationMillis = 240, easing = EmphasizedDecel)
    fun <T> settle(): FiniteAnimationSpec<T> = tween(durationMillis = 180, easing = Standard)
    fun <T> springBack(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 500f)
}
