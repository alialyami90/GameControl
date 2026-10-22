package com.gamecontroller.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Light confirm click for presses and latch toggles. CONFIRM (API 30+) is short enough for
 * rapid tapping; older devices fall back to the long-press buzz.
 */
@Composable
fun rememberPressHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
        { view.performHapticFeedback(type) }
    }
}
