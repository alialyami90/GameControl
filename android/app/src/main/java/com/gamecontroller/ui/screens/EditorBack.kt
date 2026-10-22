package com.gamecontroller.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** System Back inside the editor behaves like the X: it never saves. */
@Composable
internal fun EditorBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)
