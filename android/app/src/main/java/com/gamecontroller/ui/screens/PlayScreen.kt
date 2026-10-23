package com.gamecontroller.ui.screens

import com.gamecontroller.model.Joystick
import com.gamecontroller.model.ControlType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import com.gamecontroller.ui.components.rememberPressHaptic
import androidx.compose.foundation.layout.heightIn
import com.gamecontroller.model.HidStatus
import com.gamecontroller.model.ConnectionStatus
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.gamecontroller.ui.components.SlantStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    imperialUnits: Boolean = false,
    onOpenConnection: (() -> Unit)? = null,
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
        PlayHud(state, pageIndex, imperialUnits, onPageChange, onEdit, onBack, onOpenConnection)
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) {
            if (page == null || page.controls.isEmpty()) {
                EmptyPage(onEdit)
            } else {
                ControlGrid(page) { control, mod ->
                    // A joystick is two axes to everything past the UI: "<id>.x" and "<id>.y".
                    val stick = control.type == ControlType.JOYSTICK
                    val xId = if (stick) control.id + Joystick.X else control.id
                    ControlView(
                        control = control,
                        value = state.values[xId] ?: 0f,
                        indicator = state.indicators[control.id],
                        enabled = control.id !in state.unavailable,
                        onValue = { onControlValue(xId, it) },
                        onStep = { onRotaryStep(control.id, it) },
                        modifier = mod,
                        valueY = if (stick) state.values[control.id + Joystick.Y] ?: 0f else 0f,
                        onValueY = { if (stick) onControlValue(control.id + Joystick.Y, it) },
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
private fun PlayHud(
    state: PlayState,
    pageIndex: Int,
    imperial: Boolean,
    onPageChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenConnection: (() -> Unit)?,
) {
    // While anything is linked the player is probably mid-game: Edit then needs a hold, and a
    // stray tap only says so, instead of dropping them into the editor.
    val live = state.game.isNotEmpty() || state.connection.status == ConnectionStatus.CONNECTED ||
        state.hid.status == HidStatus.CONNECTED
    var holdHint by remember { mutableStateOf(false) }
    // A dropped press (no link could carry it) flashes both badges red for 600ms with a double tick.
    var noLink by remember { mutableStateOf(false) }
    var seenDrops by remember { mutableStateOf(state.linkDrops) }
    val haptic = rememberPressHaptic()
    LaunchedEffect(state.linkDrops) {
        if (state.linkDrops != seenDrops) {
            seenDrops = state.linkDrops
            noLink = true
            haptic(); delay(90); haptic()
            delay(510)
            noLink = false
        }
    }
    LaunchedEffect(holdHint) { if (holdHint) { delay(1600); holdHint = false } }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        // Link badges drop their "BT"/"PC" names only when the strip would otherwise overflow;
        // their status words, lamps, Edit and Back always stay.
        val pages = state.profile.pages.size
        val needed = 48.dp + 200.dp + (if (pages > 1) 112.dp * pages else 0.dp) +
            (if (state.game.isNotEmpty()) 280.dp else 0.dp) + 300.dp + 130.dp
        val compact = maxWidth < needed
        // Narrowest phones (640dp class): lamp-only badges, no rpm, shorter name, so Edit stays on screen.
        val tight = maxWidth < 720.dp
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to profiles", tint = Race.InkMuted)
            }
            Column(Modifier.widthIn(min = if (tight) 72.dp else 96.dp, max = if (tight) 100.dp else if (compact) 180.dp else 240.dp).padding(end = if (tight) 8.dp else 14.dp)) {
                // Tight: the condensed cap style on up to two lines, so names read instead of "BeamN...".
                Text(
                    if (tight) state.profile.name.uppercase() else state.profile.name,
                    style = if (tight) RaceType.capLabel else RaceType.title,
                    color = Race.Ink,
                    maxLines = if (tight) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The game tag only adds information when the name doesn't already say it.
                val tag = state.profile.game.tag()
                if (!tight && !state.profile.name.contains(tag, ignoreCase = true) && !tag.contains(state.profile.name, ignoreCase = true)) {
                    Text(tag.uppercase(), style = RaceType.unit, color = Race.InkFaint, maxLines = 1)
                }
            }
            Box(Modifier.weight(1f)) {
                if (state.profile.pages.size > 1) {
                    if (tight) {
                        // Smallest phones: a pager can't clip the way a scrolled tab row can.
                        PagePager(state.profile.pages.map { it.name }, pageIndex, onPageChange)
                    } else {
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            PageTabs(state.profile.pages.map { it.name }, pageIndex, onPageChange)
                        }
                    }
                }
            }
            GameReadouts(state.game, Modifier.padding(start = if (tight) 4.dp else 12.dp, end = if (tight) 8.dp else 14.dp), imperial = imperial, showRpm = !tight)
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .then(if (onOpenConnection != null) Modifier.clip(SlantShape()).clickable(onClickLabel = "Open connections", onClick = onOpenConnection) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BluetoothLinkBadge(state.hid, compact = compact, lampOnly = tight, alert = noLink)
                PcLinkBadge(state.connection, compact = compact, lampOnly = tight, alert = noLink)
            }
            Spacer(Modifier.width(10.dp))
            // Tight: Edit goes icon-only so the page pager keeps room for the page name.
            SlantButton(
                text = if (holdHint) "Hold" else if (tight) "" else "Edit",
                icon = Icons.Rounded.Tune,
                onClick = if (live) ({ holdHint = true }) else onEdit,
                onLongClick = if (live) onEdit else null,
                longClickLabel = "Edit layout",
                modifier = Modifier.widthIn(min = if (tight) 64.dp else 104.dp).semantics { contentDescription = "Edit layout" },
            )
        }
    }
}

/** Page switcher: slanted plates, the current page filled electric. */
@Composable
fun PageTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    SlantTabs(names, selected, onSelect, modifier)

/** Previous / next with the current page name between: always fits, two 48dp targets. */
@Composable
private fun PagePager(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(selected - 1) }, enabled = selected > 0) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page", tint = if (selected > 0) Race.Ink else Race.InkFaint)
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 36.dp)
                .clip(SlantShape())
                .background(Race.Electric)
                .padding(horizontal = 10.dp)
                .semantics { contentDescription = "Page ${selected + 1} of ${names.size}: ${names[selected]}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                names[selected].uppercase(),
                style = RaceType.capLabel.copy(fontStyle = FontStyle.Italic),
                color = OnAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onSelect(selected + 1) }, enabled = selected < names.lastIndex) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page", tint = if (selected < names.lastIndex) Race.Ink else Race.InkFaint)
        }
    }
}

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
        SlantButton("Open the editor", onEdit, icon = Icons.Rounded.Tune, style = SlantStyle.PRIMARY)
    }
}
