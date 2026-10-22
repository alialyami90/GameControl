package com.gamecontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.Control
import com.gamecontroller.model.GameId
import com.gamecontroller.model.Page
import com.gamecontroller.model.PlayState
import com.gamecontroller.ui.components.BluetoothLinkBadge
import com.gamecontroller.ui.components.ControlView
import com.gamecontroller.ui.components.GameReadouts
import com.gamecontroller.ui.components.PcLinkBadge
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantTabs
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.OnAccent
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape

/** Gap between grid cells on the play surface and in the editor. */
val GridGap = 10.dp

/** Pixel geometry of a page's grid inside a given box. */
data class GridMetrics(val cellW: Dp, val cellH: Dp, val gap: Dp) {
    fun x(col: Int) = (cellW + gap) * col
    fun y(row: Int) = (cellH + gap) * row
    fun w(span: Int) = cellW * span + gap * (span - 1)
    fun h(span: Int) = cellH * span + gap * (span - 1)
}

fun gridMetrics(page: Page, width: Dp, height: Dp, gap: Dp = GridGap) = GridMetrics(
    cellW = (width - gap * (page.columns - 1)) / page.columns,
    cellH = (height - gap * (page.rows - 1)) / page.rows,
    gap = gap,
)

/**
 * The control surface. Edge-to-edge landscape: a slim HUD strip on top and the page's grid
 * filling everything below, so every control is as large as the layout allows.
 */
@Composable
fun PlayScreen(
    state: PlayState,
    onControlValue: (controlId: String, value: Float) -> Unit,
    onRotaryStep: (controlId: String, delta: Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val pages = state.profile.pages
    val pageIndex = state.pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(pageIndex)
    Column(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PlayHud(state, pageIndex, onPageChange, onEdit, onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) {
            if (page == null || page.controls.isEmpty()) {
                EmptyPage(onEdit)
            } else {
                ControlGrid(page) { control, mod ->
                    ControlView(
                        control = control,
                        value = state.values[control.id] ?: 0f,
                        indicator = state.indicators[control.id],
                        enabled = control.id !in state.unavailable,
                        onValue = { onControlValue(control.id, it) },
                        onStep = { onRotaryStep(control.id, it) },
                        modifier = mod,
                    )
                }
            }
        }
    }
}

/** Lays out a page's controls on its grid; [content] draws one control into the given modifier. */
@Composable
fun ControlGrid(page: Page, modifier: Modifier = Modifier, content: @Composable (Control, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val m = gridMetrics(page, maxWidth, maxHeight)
        for (c in page.controls) {
            content(
                c,
                Modifier
                    .offset(m.x(c.x), m.y(c.y))
                    .size(m.w(c.w.coerceAtLeast(1)), m.h(c.h.coerceAtLeast(1))),
            )
        }
    }
}

@Composable
private fun PlayHud(state: PlayState, pageIndex: Int, onPageChange: (Int) -> Unit, onEdit: () -> Unit, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to profiles", tint = Race.InkMuted)
        }
        Column(Modifier.widthIn(min = 96.dp, max = 220.dp).padding(end = 16.dp)) {
            Text(
                state.profile.name,
                style = RaceType.capLabelLarge,
                color = Race.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (state.profile.game == GameId.CUSTOM) "Any game" else state.profile.game.displayName).uppercase(),
                style = RaceType.unit,
                color = Race.InkFaint,
                maxLines = 1,
            )
        }
        if (state.profile.pages.size > 1) {
            PageTabs(state.profile.pages.map { it.name }, pageIndex, onPageChange)
        }
        Spacer(Modifier.weight(1f))
        GameReadouts(state.game, Modifier.padding(end = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BluetoothLinkBadge(state.hid)
            PcLinkBadge(state.connection)
        }
        Spacer(Modifier.width(10.dp))
        SlantButton(text = "Edit", icon = Icons.Rounded.Tune, onClick = onEdit)
    }
}

/** Page switcher: slanted plates, the current page filled electric. */
@Composable
fun PageTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    SlantTabs(names, selected, onSelect, modifier)

@Composable
private fun EmptyPage(onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("This page is empty", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Race.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add buttons, switches and sliders in the editor.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = Race.InkMuted,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onEdit) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open the editor")
        }
    }
}
