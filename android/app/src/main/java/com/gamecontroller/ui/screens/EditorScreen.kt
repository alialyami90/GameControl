package com.gamecontroller.ui.screens

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
}

fun ControlType.blurb(): String = when (this) {
    ControlType.MOMENTARY -> "On while held"
    ControlType.LATCHING -> "Tap on, tap off"
    ControlType.SWITCH -> "One lever, several positions"
    ControlType.RADIO -> "Separate keys, one active"
    ControlType.SLIDER -> "Axis, optional spring return"
    ControlType.ROTARY -> "Turn for steps"
}

private fun ControlType.paletteIcon(): ImageVector? = IconLibrary[
    when (this) {
        ControlType.MOMENTARY -> "horn"
        ControlType.LATCHING -> "power"
        ControlType.SWITCH -> "gearbox"
        ControlType.RADIO -> "camera_cycle"
        ControlType.SLIDER -> "suspension"
        ControlType.ROTARY -> "cruise"
    }
]

/** First cell not covered by any control, scanning rows top to bottom. */
private fun firstFreeCell(page: Page): Pair<Int, Int> {
    for (y in 0 until page.rows) for (x in 0 until page.columns) {
        val taken = page.controls.any { x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }
        if (!taken) return x to y
    }
    return 0 to 0
}

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
) {
    val profile = state.profile
    val pageIndex = state.pageIndex.coerceIn(0, (profile.pages.size - 1).coerceAtLeast(0))
    val page = profile.pages.getOrNull(pageIndex) ?: Page("p1", "Main")
    val selected = page.controls.firstOrNull { it.id == state.selectedControlId }
    var confirmDiscard by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Top bar.
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (state.dirty) confirmDiscard = true else onDiscard() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Close without saving", tint = Race.InkMuted)
            }
            Column(Modifier.padding(end = 16.dp)) {
                Text("EDIT LAYOUT", style = RaceType.unit, color = Race.Electric)
                Text(profile.name, style = RaceType.capLabelLarge, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SlantTabs(profile.pages.map { it.name }, pageIndex, onPageChange)
                IconButton(onClick = onAddPage) { Icon(Icons.Rounded.Add, contentDescription = "Add page", tint = Race.InkMuted) }
            }
            if (state.dirty) {
                Text("UNSAVED", style = RaceType.unit, color = Race.Amber, modifier = Modifier.padding(end = 12.dp))
            }
            SlantButton("Done", onDone, style = SlantStyle.PRIMARY)
        }
        Row(Modifier.fillMaxSize()) {
            Palette(onAdd = { type -> val (x, y) = firstFreeCell(page); onAddControl(type, x, y) })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
            ) {
                EditCanvas(page, state.selectedControlId, onSelect, onMove, onResize)
            }
            Column(
                Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .background(Race.Panel)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (selected != null) {
                    Inspector(selected, page, profile.game, beamngCommands, onUpdate, onResize, onDelete, onClose = { onSelect(null) })
                } else {
                    PageAndProfile(
                        profile, page, pageIndex,
                        onRenamePage, onDeletePage, onGridChange, onProfileChange, onApplyGameBindings,
                    )
                }
            }
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

@Composable
private fun Palette(onAdd: (ControlType) -> Unit) {
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("ADD", style = RaceType.unit, color = Race.InkFaint, modifier = Modifier.padding(start = 4.dp))
        ControlType.entries.forEach { t ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CapShape(cut = 10.dp, radius = 4.dp))
                    .background(Race.Cap)
                    .clickable { onAdd(t) }
                    .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Add ${t.displayName()}: ${t.blurb()}" }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                t.paletteIcon()?.let { Icon(it, contentDescription = null, tint = Race.Ink, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.height(6.dp))
                Text(t.displayName().uppercase(), style = RaceType.unit, color = Race.InkMuted, textAlign = TextAlign.Center, maxLines = 2)
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
    // Resize previews snap to whole cells.
    val nw = (c.w + (grow.x / pitchX).roundToInt()).coerceIn(1, page.columns - c.x)
    val nh = (c.h + (grow.y / pitchY).roundToInt()).coerceIn(1, page.rows - c.y)

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
            // Resize tab.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .padding(4.dp)
                    .clip(SlantShape())
                    .background(Race.Electric)
                    .pointerInput(c.id, pitchX, pitchY) {
                        detectDragGestures(
                            onDragEnd = {
                                val w = (current.w + (grow.x / pitchX).roundToInt()).coerceIn(1, page.columns - current.x)
                                val h = (current.h + (grow.y / pitchY).roundToInt()).coerceIn(1, page.rows - current.y)
                                grow = Offset.Zero
                                if (w != current.w || h != current.h) onResize(current.id, w, h)
                            },
                            onDragCancel = { grow = Offset.Zero },
                        ) { change, amount -> change.consume(); grow += amount }
                    }
                    .semantics { contentDescription = "Resize ${c.label}" },
            )
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
    if (profile.game == GameId.BEAMNG) {
        SectionHeader("BeamNG")
        Hint("Writes this profile's gamepad buttons into BeamNG's input bindings, so each control works in game without setup.")
        Gap(8)
        SlantButton("Bind in game", onApplyGameBindings, icon = Icons.Rounded.Link, style = SlantStyle.OUTLINE)
    }
    Gap(24)
}
