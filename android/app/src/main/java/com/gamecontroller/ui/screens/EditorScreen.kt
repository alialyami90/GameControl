package com.gamecontroller.ui.screens

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.Brush
import com.gamecontroller.ui.components.StatusLamp
import com.gamecontroller.model.BindResult
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.EditorState
import com.gamecontroller.model.GameId
import com.gamecontroller.model.OutputRoute
import com.gamecontroller.model.Page
import com.gamecontroller.model.Profile
import com.gamecontroller.ui.components.ChoiceChips
import com.gamecontroller.ui.components.ControlView
import com.gamecontroller.ui.components.Field
import com.gamecontroller.ui.components.Gap
import com.gamecontroller.ui.components.Hint
import com.gamecontroller.ui.components.SectionHeader
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantStyle
import com.gamecontroller.ui.components.SlantTabs
import com.gamecontroller.ui.components.StateTag
import com.gamecontroller.ui.components.Stepper
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape
import kotlin.math.roundToInt

/** Name and icon for each control type, as the editor presents them. */
fun ControlType.displayName(): String = when (this) {
    ControlType.MOMENTARY -> "Button"
    ControlType.LATCHING -> "Toggle"
    ControlType.SWITCH -> "Selector"
    ControlType.RADIO -> "Button group"
    ControlType.SLIDER -> "Slider"
    ControlType.ROTARY -> "Knob"
    ControlType.JOYSTICK -> "Joystick"
}

fun ControlType.blurb(): String = when (this) {
    ControlType.MOMENTARY -> "On while held"
    ControlType.LATCHING -> "Tap on, tap off"
    ControlType.SWITCH -> "One lever, several positions"
    ControlType.RADIO -> "Separate keys, one active"
    ControlType.SLIDER -> "Axis, optional spring return"
    ControlType.ROTARY -> "Turn for steps"
    ControlType.JOYSTICK -> "Two axes on one stick, springs to centre"
}

private fun ControlType.paletteIcon(): ImageVector? = IconLibrary[
    when (this) {
        ControlType.MOMENTARY -> "horn"
        ControlType.LATCHING -> "power"
        ControlType.SWITCH -> "gearbox"
        ControlType.RADIO -> "camera_cycle"
        ControlType.SLIDER -> "suspension"
        ControlType.ROTARY -> "cruise"
        ControlType.JOYSTICK -> "swing"
    }
]

/** First cell not covered by any control, scanning rows top to bottom; null when the page is full. */
private fun firstFreeCell(page: Page): Pair<Int, Int>? {
    for (y in 0 until page.rows) for (x in 0 until page.columns) {
        val taken = page.controls.any { x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }
        if (!taken) return x to y
    }
    return null
}

/** Below this width the inspector slides over the canvas and the palette becomes an icon rail. */
private val WideEditor = 840.dp

/**
 * Layout editor: the workshop. Palette of control types on the left, the page grid in the
 * middle at play scale, and the inspector (or page and profile settings) on the right.
 */
@Composable
fun EditorScreen(
    state: EditorState,
    beamngCommands: List<String>,
    onSelect: (String?) -> Unit,
    onAddControl: (ControlType, Int, Int) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (Control) -> Unit,
    onAddPage: () -> Unit,
    onRenamePage: (Int, String) -> Unit,
    onDeletePage: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onGridChange: (columns: Int, rows: Int) -> Unit,
    onProfileChange: ((Profile) -> Profile) -> Unit,
    onApplyGameBindings: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit,
    onPreviewGameBindings: (() -> Pair<String, List<Pair<String, Int>>>)? = null,
) {
    val profile = state.profile
    val pageIndex = state.pageIndex.coerceIn(0, (profile.pages.size - 1).coerceAtLeast(0))
    val page = profile.pages.getOrNull(pageIndex) ?: Page("p1", "Main")
    val selected = page.controls.firstOrNull { it.id == state.selectedControlId }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val freeCell = firstFreeCell(page)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentProfile by rememberUpdatedState(profile)

    // Deletes take effect at once and offer Undo, which puts back only what was deleted, so
    // edits made while the snackbar is up survive.
    fun undoable(message: String, act: () -> Unit, undo: () -> Unit) {
        act()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
        }
    }
    val deleteControl: (String) -> Unit = { id ->
        val gone = page.controls.firstOrNull { it.id == id }
        val pageId = page.id
        if (gone != null) undoable("${gone.label.ifBlank { "Control" }} deleted", { onDelete(id) }) {
            onProfileChange { p ->
                p.copy(pages = p.pages.map { pg -> if (pg.id == pageId && pg.controls.none { it.id == id }) pg.copy(controls = pg.controls + gone) else pg })
            }
        }
    }
    val deletePage: (Int) -> Unit = { i ->
        val gone = currentProfile.pages.getOrNull(i)
        if (gone != null) undoable("Page ${gone.name} deleted", { onDeletePage(i) }) {
            onProfileChange { p ->
                if (p.pages.any { it.id == gone.id }) p
                else p.copy(pages = p.pages.toMutableList().apply { add(i.coerceAtMost(size), gone) })
            }
            onPageChange(i)
        }
    }

    // System Back is the X: it asks before throwing edits away and never saves. Done is the only save.
    EditorBackHandler { if (state.dirty) confirmDiscard = true else onDiscard() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= WideEditor
        val panelOpen = selected != null || (!wide && showSettings)
        Column(Modifier.fillMaxSize()) {
            // Top bar.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (state.dirty) confirmDiscard = true else onDiscard() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close without saving", tint = Race.InkMuted)
                }
                Row(Modifier.widthIn(max = if (wide) 300.dp else 150.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = RaceType.title, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (wide) {
                        Spacer(Modifier.width(10.dp))
                        StateTag("Editing", quiet = true)
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    SlantTabs(profile.pages.map { it.name }, pageIndex, onPageChange)
                    IconButton(onClick = onAddPage) { Icon(Icons.Rounded.Add, contentDescription = "Add page", tint = Race.InkMuted) }
                }
                if (!wide) {
                    IconButton(onClick = { onSelect(null); showSettings = !showSettings }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Page and profile settings", tint = if (showSettings) Race.Electric else Race.InkMuted)
                    }
                }
                if (state.dirty) {
                    Text("UNSAVED", style = RaceType.unit, color = Race.Amber, modifier = Modifier.padding(end = 12.dp))
                }
                SlantButton("Done", onDone, style = SlantStyle.PRIMARY)
            }
            Row(Modifier.fillMaxSize()) {
                Palette(
                    rail = !wide,
                    full = freeCell == null,
                    onAdd = { type -> freeCell?.let { (x, y) -> onAddControl(type, x, y) } },
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (freeCell == null) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Page is full: add a page or add rows.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Race.InkMuted,
                                modifier = Modifier.weight(1f),
                            )
                            if (page.rows < 8) {
                                SlantButton("Add a row", { onGridChange(page.columns, page.rows + 1) }, style = SlantStyle.QUIET)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    ) {
                        EditCanvas(page, state.selectedControlId, { onSelect(it); if (it != null) showSettings = false }, onMove, onResize)
                    }
                }
                if (wide) {
                    Column(
                        Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(Race.Panel)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        SidePanel(selected, page, pageIndex, state, beamngCommands, onUpdate, onResize, deleteControl, onSelect, onRenamePage, deletePage, onGridChange, onProfileChange, onApplyGameBindings, onPreviewGameBindings)
                    }
                }
            }
        }

        // Narrow: the panel slides over the canvas from the right, with a scrim that closes it.
        if (!wide) {
            AnimatedVisibility(panelOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Race.Ground.copy(alpha = 0.6f))
                        .clickable(onClickLabel = "Close panel") { onSelect(null); showSettings = false },
                )
            }
            AnimatedVisibility(
                panelOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                Column(
                    Modifier
                        .width(minOf(340.dp, maxWidth * 0.8f))
                        .fillMaxHeight()
                        .background(Race.Panel)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    if (selected == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Settings", style = RaceType.title, color = Race.Ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close settings", tint = Race.InkMuted)
                            }
                        }
                    }
                    SidePanel(selected, page, pageIndex, state, beamngCommands, onUpdate, onResize, deleteControl, onSelect, onRenamePage, deletePage, onGridChange, onProfileChange, onApplyGameBindings, onPreviewGameBindings)
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) { data ->
            Snackbar(
                data,
                containerColor = Race.PanelRaised,
                contentColor = Race.Ink,
                actionColor = Race.Electric,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Race.PanelRaised,
            title = { Text("Discard changes?") },
            text = { Text("Your edits to ${profile.name} since you opened the editor will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", color = Race.Red) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/** The inspector for a selected control, otherwise page and profile settings. */
@Composable
private fun SidePanel(
    selected: Control?,
    page: Page,
    pageIndex: Int,
    state: EditorState,
    beamngCommands: List<String>,
    onUpdate: (Control) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onRenamePage: (Int, String) -> Unit,
    onDeletePage: (Int) -> Unit,
    onGridChange: (Int, Int) -> Unit,
    onProfileChange: ((Profile) -> Profile) -> Unit,
    onApplyGameBindings: () -> Unit,
    onPreviewGameBindings: (() -> Pair<String, List<Pair<String, Int>>>)?,
) {
    if (selected != null) {
        Inspector(selected, page, state.profile.game, beamngCommands, onUpdate, onResize, onDelete, onClose = { onSelect(null) }, stateKeys = state.stateKeys)
    } else {
        PageAndProfile(state.profile, page, pageIndex, onRenamePage, onDeletePage, onGridChange, onProfileChange, onApplyGameBindings)
        if (state.profile.game == GameId.BEAMNG) {
            BindInGame(state.profile, state.canBindInGame, state.bindResult, onApplyGameBindings, onPreviewGameBindings)
        }
        Gap(24)
    }
}

/**
 * Control types to add. Wide: labelled caps in a 104dp column. Narrow: a 56dp icon rail (names
 * stay in the accessibility label). [full] disables every item; the canvas says why.
 */
@Composable
private fun Palette(rail: Boolean, full: Boolean, onAdd: (ControlType) -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.width(if (rail) 56.dp else 104.dp).fillMaxHeight()) {
        PaletteItems(rail, full, onAdd, scroll)
        // Scroll cue: a lit fade and a chevron at the bottom while more items are below.
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Race.Ground.copy(alpha = 0f), Race.CapHigh))),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Race.Ink, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PaletteItems(rail: Boolean, full: Boolean, onAdd: (ControlType) -> Unit, scroll: androidx.compose.foundation.ScrollState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = if (rail) 4.dp else 12.dp, end = if (rail) 4.dp else 8.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (rail) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ADD", style = RaceType.unit, color = Race.InkFaint, modifier = if (rail) Modifier else Modifier.align(Alignment.Start).padding(start = 4.dp))
        ControlType.entries.forEach { t ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(CapShape(cut = if (rail) 8.dp else 10.dp, radius = 4.dp))
                    .background(if (full) Race.Panel else Race.Cap)
                    .clickable(enabled = !full) { onAdd(t) }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = "Add ${t.displayName()}: ${t.blurb()}"
                        if (full) stateDescription = "Page is full"
                    }
                    .padding(vertical = if (rail) 12.dp else 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val ink = if (full) Race.InkFaint else Race.Ink
                t.paletteIcon()?.let { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp)) }
                if (!rail) {
                    Spacer(Modifier.height(6.dp))
                    Text(t.displayName().uppercase(), style = RaceType.unit, color = if (full) Race.InkFaint else Race.InkMuted, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/** The page grid at play proportions. Drag a control to move it, drag its corner tab to resize. */
@Composable
private fun EditCanvas(
    page: Page,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { currentOnSelect(null) } },
    ) {
        val m = gridMetrics(page, maxWidth, maxHeight)
        val density = LocalDensity.current
        val pitchX = with(density) { (m.cellW + m.gap).toPx() }
        val pitchY = with(density) { (m.cellH + m.gap).toPx() }
        // Empty slots.
        Canvas(Modifier.fillMaxSize()) {
            val shape = CapShape(cut = 10.dp, radius = 4.dp)
            for (x in 0 until page.columns) for (y in 0 until page.rows) {
                val covered = page.controls.any { x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }
                if (covered) continue
                val left = x * pitchX; val top = y * pitchY
                val w = m.cellW.toPx(); val h = m.cellH.toPx()
                translate(left, top) {
                    drawOutline(
                        shape.createOutline(androidx.compose.ui.geometry.Size(w, h), layoutDirection, this),
                        Race.Line,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                    )
                }
            }
        }
        for (c in page.controls) {
            key(c.id) {
                EditableControl(c, page, c.id == selectedId, m, pitchX, pitchY, onSelect, onMove, onResize)
            }
        }
    }
}

@Composable
private fun EditableControl(
    c: Control,
    page: Page,
    selected: Boolean,
    m: GridMetrics,
    pitchX: Float,
    pitchY: Float,
    onSelect: (String?) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var grow by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(c)
    val dragging = drag != Offset.Zero
    // Resize previews snap to whole cells and stop at the control's minimum span.
    val nw = (c.w + (grow.x / pitchX).roundToInt()).coerceIn(widthRange(c, page))
    val nh = (c.h + (grow.y / pitchY).roundToInt()).coerceIn(heightRange(c, page))

    Box(
        Modifier
            .offset(m.x(c.x), m.y(c.y))
            .size(m.w(nw), m.h(nh))
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                if (dragging) { scaleX = 1.03f; scaleY = 1.03f; alpha = 0.9f }
            }
            .pointerInput(c.id) { detectTapGestures { onSelect(current.id) } }
            .pointerInput(c.id, pitchX, pitchY) {
                detectDragGestures(
                    onDragStart = { onSelect(current.id) },
                    onDragEnd = {
                        val tx = (current.x + (drag.x / pitchX).roundToInt()).coerceIn(0, page.columns - current.w)
                        val ty = (current.y + (drag.y / pitchY).roundToInt()).coerceIn(0, page.rows - current.h)
                        drag = Offset.Zero
                        if (tx != current.x || ty != current.y) onMove(current.id, tx, ty)
                    },
                    onDragCancel = { drag = Offset.Zero },
                ) { change, amount -> change.consume(); drag += amount }
            }
            .semantics { contentDescription = "${c.label}, ${c.type.displayName()}, column ${c.x + 1}, row ${c.y + 1}" },
    ) {
        ControlView(
            control = c,
            value = if (c.type == ControlType.SLIDER) 0.5f else 0f,
            indicator = null,
            enabled = true,
            onValue = {},
            onStep = {},
            interactive = false,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val o = CapShape().createOutline(size, layoutDirection, this)
                        drawOutline(o, Race.Electric, style = Stroke(5.dp.toPx()))
                    },
            )
            // Resize tab: never more than half the cap each way, so the rest of the cap still moves
            // it. On caps too small for a usable tab, the inspector's Width/Height steppers resize.
            val handle = minOf(48.dp, m.w(c.w) / 2, m.h(c.h) / 2)
            if (handle >= 32.dp) Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(handle)
                    .pointerInput(c.id, pitchX, pitchY) {
                        detectDragGestures(
                            onDragEnd = {
                                val w = (current.w + (grow.x / pitchX).roundToInt()).coerceIn(widthRange(current, page))
                                val h = (current.h + (grow.y / pitchY).roundToInt()).coerceIn(heightRange(current, page))
                                grow = Offset.Zero
                                if (w != current.w || h != current.h) onResize(current.id, w, h)
                            },
                            onDragCancel = { grow = Offset.Zero },
                        ) { change, amount -> change.consume(); grow += amount }
                    }
                    .semantics { contentDescription = "Resize ${c.label}" },
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(Modifier.padding(5.dp).size(width = 22.dp, height = 18.dp).clip(SlantShape()).background(Race.Electric))
            }
        }
    }
}

@Composable
private fun PageAndProfile(
    profile: Profile,
    page: Page,
    pageIndex: Int,
    onRenamePage: (Int, String) -> Unit,
    onDeletePage: (Int) -> Unit,
    onGridChange: (Int, Int) -> Unit,
    onProfileChange: ((Profile) -> Profile) -> Unit,
    onApplyGameBindings: () -> Unit,
) {
    Hint("Tap a control to edit it, or add one from the left.", Modifier.padding(top = 12.dp))
    SectionHeader("This page")
    Field(page.name, { onRenamePage(pageIndex, it) }, "Page name")
    Stepper("Columns", page.columns, 4..12, { onGridChange(it, page.rows) })
    Stepper("Rows", page.rows, 2..8, { onGridChange(page.columns, it) })
    if (profile.pages.size > 1) {
        Gap(8)
        SlantButton("Delete page", { onDeletePage(pageIndex) }, style = SlantStyle.OUTLINE, accent = Race.Red)
    }

    SectionHeader("Profile")
    Field(profile.name, { n -> onProfileChange { it.copy(name = n) } }, "Profile name")
    Field(profile.description, { d -> onProfileChange { it.copy(description = d) } }, "Description", hint = "Shown on the profile card")
    Gap(4)
    Text("GAME", style = RaceType.unit, color = Race.InkFaint)
    Gap(6)
    val games = listOf(GameId.CUSTOM, GameId.BEAMNG, GameId.MUDRUNNER)
    ChoiceChips(games.map { it.tag() }, profile.game.tag(), { t -> onProfileChange { p -> p.copy(game = games.first { it.tag() == t }) } })
    Gap(14)
    Text("SEND INPUT VIA", style = RaceType.unit, color = Race.InkFaint)
    Gap(6)
    val routes = mapOf(OutputRoute.AUTO to "Auto", OutputRoute.HID to "Bluetooth only", OutputRoute.COMPANION to "PC companion only")
    ChoiceChips(routes.values.toList(), routes[profile.output], { t -> onProfileChange { p -> p.copy(output = routes.entries.first { it.value == t }.key) } })
    Hint(
        when (profile.output) {
            OutputRoute.AUTO -> "Uses Bluetooth when the phone is paired as GameCtl Pad, otherwise the PC companion."
            OutputRoute.HID -> "Keyboard and gamepad over Bluetooth. BeamNG commands and Xbox controls won't work."
            OutputRoute.COMPANION -> "Everything goes through the PC companion over Wi-Fi."
        },
    )
}

/** "hid" / "vjoy1" from the companion, as a person would name the device. */
private fun bindDeviceName(device: String): String {
    val key = device.lowercase().filter { it.isLetterOrDigit() }
    return when {
        key == "hid" || key.startsWith("bluetooth") || key.contains("gamectl") -> "the Bluetooth gamepad (GameCtl Pad)"
        // "vjoy1", "vjoy 1", "vJoy device 1": keep only the device number.
        key.startsWith("vjoy") -> "vJoy device " + key.takeLastWhile { it.isDigit() }.ifBlank { "1" }
        else -> device
    }
}

/**
 * Bind in game: shows what will be written before it happens, then keeps the last result on
 * screen (with its time) instead of a passing snackbar.
 */
@Composable
private fun BindInGame(
    profile: Profile,
    canBind: Boolean,
    result: BindResult?,
    onApply: () -> Unit,
    onPreview: (() -> Pair<String, List<Pair<String, Int>>>)?,
) {
    var preview by remember { mutableStateOf<Pair<String, List<Pair<String, Int>>>?>(null) }
    SectionHeader("BeamNG")
    Hint("Writes this profile's gamepad buttons into BeamNG's input bindings, so each control works in game without setup.")
    Gap(8)
    // In progress: busy with no verdict yet. Busy *with* a message is the mod turning a second
    // request away, which is a result (red row, button stays enabled), not a wait.
    val inProgress = result != null && result.busy && !result.ok && result.message.isBlank()
    val rejected = result != null && result.busy && result.message.isNotBlank()
    // Pairs come from the editor's current (unsaved) profile, so recount whenever it changes.
    val pairCount = remember(profile) { onPreview?.invoke()?.second?.size }
    val nothingToBind = pairCount == 0
    SlantButton(
        if (inProgress) "Binding…" else "Bind in game",
        { val p = onPreview?.invoke(); if (p != null) preview = p else onApply() },
        icon = Icons.Rounded.Link,
        style = SlantStyle.OUTLINE,
        enabled = canBind && !inProgress && !nothingToBind,
    )
    when {
        !canBind -> Hint("Needs the PC companion linked and BeamNG running with the GameCtl mod.")
        nothingToBind -> Hint("Nothing to bind yet: give gamepad-button controls a \"Bind in game to\" action in the inspector.")
    }
    if (result != null && !inProgress) {
        Gap(8)
        val tone = when {
            rejected -> Race.Red
            result.ok && result.unknown.isEmpty() -> Race.Green
            result.ok || result.written -> Race.Amber
            else -> Race.Red
        }
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(result.atMillis))
        val headline = when {
            rejected -> "Not bound · $time"
            result.alreadyBound -> "Already bound · $time"
            result.ok -> "Bound ${result.count} of ${result.requested} · $time"
            else -> "Not bound · $time"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SlantShape(lean = 8.dp))
                .background(Race.Ground)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
        ) {
            StatusLamp(tone, lit = true, size = 10, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(headline, style = MaterialTheme.typography.bodyMedium, color = Race.Ink)
                Text("on ${bindDeviceName(result.device)}", style = MaterialTheme.typography.bodySmall, color = Race.InkMuted)
                if (rejected) Text(result.message.ifBlank { "BeamNG is still writing the last request. Try again in a second." }, style = MaterialTheme.typography.bodySmall, color = Race.Red)
                else if (result.message.isNotBlank() && !result.ok) Text(result.message, style = MaterialTheme.typography.bodySmall, color = Race.Red)
                if (result.unknown.isNotEmpty()) {
                    Text("BeamNG didn't recognise: " + result.unknown.joinToString(), style = MaterialTheme.typography.bodySmall, color = Race.Amber)
                }
                if (!result.ok && result.written) Text("The bindings file was written; restart BeamNG to load it.", style = MaterialTheme.typography.bodySmall, color = Race.InkMuted)
            }
        }
    }
    preview?.let { (device, pairs) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = Race.PanelRaised,
            title = { Text("Bind ${pairs.size} controls in BeamNG?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Writes to BeamNG's bindings for ${bindDeviceName(device)}. Existing bindings for these buttons are replaced.", style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted)
                    Gap(10)
                    pairs.forEach { (action, button) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("B$button", style = RaceType.readout, color = Race.Ink, modifier = Modifier.width(56.dp))
                            Text(action, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Race.Ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null; onApply() }) { Text("Bind", color = Race.Electric) } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}
