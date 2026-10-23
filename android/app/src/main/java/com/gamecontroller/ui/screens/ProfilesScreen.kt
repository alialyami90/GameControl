package com.gamecontroller.ui.screens

import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.heightIn
import com.gamecontroller.ui.components.BluetoothLinkBadge
import com.gamecontroller.model.HidUi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.ConnectionUi
import com.gamecontroller.model.ConnectionStatus
import com.gamecontroller.model.HidStatus
import com.gamecontroller.ui.AppVersion
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import com.gamecontroller.model.GameId
import com.gamecontroller.model.Page
import com.gamecontroller.model.Profile
import com.gamecontroller.ui.components.Field
import com.gamecontroller.ui.components.Gap
import com.gamecontroller.ui.components.PcLinkBadge
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantStyle
import com.gamecontroller.ui.components.SlantTabs
import com.gamecontroller.ui.components.StateTag
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.OnAccent
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape
import com.gamecontroller.ui.theme.lit

fun GameId.tag(): String = if (this == GameId.CUSTOM) "Any game" else displayName

/**
 * Profile select, laid out like a racing game's car select: each profile is a card carrying a
 * live miniature of its first page, so you recognise a layout by its shape.
 */
@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    connection: ConnectionUi,
    hid: HidUi,
    onOpen: (String) -> Unit,
    onCreate: (String, GameId) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    onOpenConnection: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }
    var confirmRestore by remember { mutableStateOf<Profile?>(null) }
    // First run, or both links down: the list leads with the way to reach Windows.
    val linked = hid.status == HidStatus.CONNECTED || connection.status == ConnectionStatus.CONNECTED

    Box(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PROFILES", style = RaceType.plate, color = Race.Ink)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${profiles.size}",
                    style = RaceType.readout,
                    color = Race.InkFaint,
                )
                Spacer(Modifier.weight(1f))
                // Both links in one target that opens Connect; icons name the link, so the badges run compact.
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(SlantShape())
                        .clickable(onClickLabel = "Open connections", onClick = onOpenConnection)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BluetoothLinkBadge(hid, compact = true)
                    PcLinkBadge(connection, compact = true)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onImport) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "Import a profile from a file", tint = Race.InkMuted)
                }
            }
            if (profiles.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!linked) {
                        ConnectFirstRow(onOpenConnection)
                        Gap(24)
                    }
                    Text("No profiles yet", style = MaterialTheme.typography.headlineSmall, color = Race.Ink)
                    Gap(6)
                    Text("Make one for any game, or import a profile file.", style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // New profile sits in the list as its first row, so nothing floats over the cards.
                    if (!linked) {
                        item(key = "connect", span = { GridItemSpan(maxLineSpan) }) {
                            ConnectFirstRow(onOpenConnection)
                        }
                    }
                    item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
                        NewProfileRow { creating = true }
                    }
                    items(profiles, key = { it.id }) { p ->
                        ProfileCard(
                            profile = p,
                            onOpen = { onOpen(p.id) },
                            onDuplicate = { onDuplicate(p.id) },
                            onExport = { onExport(p.id) },
                            onDelete = { if (p.builtIn) confirmRestore = p else confirmDelete = p },
                        )
                    }
                    item(key = "about", span = { GridItemSpan(maxLineSpan) }) {
                        AboutLine()
                    }
                }
            }
        }
        if (profiles.isEmpty()) {
            SlantButton(
                text = "New profile",
                icon = Icons.Rounded.Add,
                onClick = { creating = true },
                style = SlantStyle.PRIMARY,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }

    if (creating) {
        NewProfileDialog(onDismiss = { creating = false }, onCreate = { n, g -> creating = false; onCreate(n, g) })
    }
    confirmRestore?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore ${p.name}?") },
            text = { Text("Restore the original ${p.name} layout? Your changes on this phone will be lost. Duplicate it first to keep them.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = null; onDelete(p.id) }) { Text("Restore", color = Race.Red) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmRestore = null }) { Text("Keep it") }
                    TextButton(onClick = { confirmRestore = null; onDuplicate(p.id) }) { Text("Duplicate instead") }
                }
            },
            containerColor = Race.PanelRaised,
        )
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("This removes the profile and its ${p.pages.size} pages from this phone. Export it first if you might want it back.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(p.id) }) { Text("Delete", color = Race.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep it") } },
            containerColor = Race.PanelRaised,
        )
    }
}

/**
 * Lead-in shown while neither link is up: names both routes to Windows and opens Connect.
 * It leaves on its own once Bluetooth or the companion app connects.
 */
@Composable
private fun ConnectFirstRow(onOpenConnection: () -> Unit) {
    val shape = CapShape(cut = 16.dp, radius = 8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(Race.PanelRaised)
            .border(1.dp, Race.Electric.copy(alpha = 0.55f), shape)
            .clickable(onClickLabel = "Open connections", onClick = onOpenConnection)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = Race.Electric, modifier = Modifier.size(22.dp))
            Icon(Icons.Rounded.Computer, contentDescription = null, tint = Race.Electric, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("CONNECT TO YOUR PC", style = RaceType.capLabelLarge.copy(fontStyle = FontStyle.Italic), color = Race.Ink)
            Text(
                "Pair over Bluetooth to drive any Windows game, or run the companion app for BeamNG lights and gauges.",
                style = MaterialTheme.typography.bodySmall,
                color = Race.InkMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        SlantButton(text = "Connect", onClick = onOpenConnection, style = SlantStyle.PRIMARY)
    }
}

/** Quiet footer at the end of the list: app name and version, read from the build. */
@Composable
private fun AboutLine() {
    Text(
        "GameCtl $AppVersion",
        style = RaceType.unit,
        color = Race.InkFaint,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "GameCtl version $AppVersion" },
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Full-width first row of the list: a dashed slot to start a new profile, like an empty garage bay. */
@Composable
private fun NewProfileRow(onClick: () -> Unit) {
    val shape = CapShape(cut = 16.dp, radius = 8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .clickable(onClickLabel = "Create a new profile", onClick = onClick)
            .drawBehind {
                drawOutline(
                    shape.createOutline(size, layoutDirection, this),
                    Race.LineStrong,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(SlantShape()).background(Race.Electric), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("NEW PROFILE", style = RaceType.capLabelLarge.copy(fontStyle = FontStyle.Italic), color = Race.Ink)
            Text("Start empty for any game, or duplicate a built-in from its menu.", style = MaterialTheme.typography.bodySmall, color = Race.InkMuted)
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = CapShape(cut = 22.dp, radius = 8.dp)
    val controls = profile.pages.sumOf { it.controls.size }
    Column(
        Modifier
            .clip(shape)
            .background(Race.Panel)
            .border(1.dp, Race.Line, shape)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "${profile.name}, ${profile.game.tag()}" },
    ) {
        LayoutMiniature(
            profile.pages.firstOrNull(),
            Modifier.fillMaxWidth().height(120.dp).background(Race.Ground.copy(alpha = 0.6f)).padding(14.dp),
        )
        Column(Modifier.padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Race.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${profile.name}", tint = Race.InkMuted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Export to file") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(
                            text = { Text(if (profile.builtIn) "Restore original layout" else "Delete", color = if (profile.builtIn) Race.Ink else Race.Red) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (profile.description.isNotBlank()) {
                Text(
                    profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Race.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 14.dp)) {
                StateTag(profile.game.tag(), quiet = profile.game == GameId.CUSTOM)
                if (profile.builtIn) {
                    Spacer(Modifier.width(8.dp))
                    Text("BUILT-IN", style = RaceType.unit, color = Race.InkFaint)
                }
                Spacer(Modifier.weight(1f))
                Stat(profile.pages.size, if (profile.pages.size == 1) "PAGE" else "PAGES")
                Spacer(Modifier.width(14.dp))
                Stat(controls, if (controls == 1) "CONTROL" else "CONTROLS")
            }
        }
    }
}

@Composable
private fun Stat(value: Int, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value.toString(), style = RaceType.readout, color = Race.Ink)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = RaceType.unit, color = Race.InkFaint, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** A to-scale sketch of a page: each control as a small cut-corner block in its accent colour. */
@Composable
fun LayoutMiniature(page: Page?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (page == null) return@Canvas
        val gap = 3.dp.toPx()
        val cw = (size.width - gap * (page.columns - 1)) / page.columns
        val ch = (size.height - gap * (page.rows - 1)) / page.rows
        // Empty cells as faint dots so a sparse layout still shows its grid.
        for (cx in 0 until page.columns) for (cy in 0 until page.rows) {
            drawCircle(Race.Line, 1.2.dp.toPx(), Offset(cx * (cw + gap) + cw / 2, cy * (ch + gap) + ch / 2))
        }
        for (c in page.controls) {
            val x = c.x * (cw + gap); val y = c.y * (ch + gap)
            val w = cw * c.w + gap * (c.w - 1); val h = ch * c.h + gap * (c.h - 1)
            val cut = minOf(w, h) * 0.25f
            val path = Path().apply {
                moveTo(x, y); lineTo(x + w - cut, y); lineTo(x + w, y + cut); lineTo(x + w, y + h); lineTo(x, y + h); close()
            }
            drawPath(path, Race.Cap)
            drawPath(path, c.accent.lit().copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
            drawRect(c.accent.lit(), Offset(x, y), Size(w * 0.45f, 2.dp.toPx()))
        }
    }
}

@Composable
private fun NewProfileDialog(onDismiss: () -> Unit, onCreate: (String, GameId) -> Unit) {
    var name by remember { mutableStateOf("") }
    val games = listOf(GameId.CUSTOM, GameId.BEAMNG, GameId.MUDRUNNER)
    var game by remember { mutableStateOf(GameId.CUSTOM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Race.PanelRaised,
        title = { Text("New profile") },
        text = {
            Column {
                Field(name, { name = it }, "Name", hint = "For example: Euro Truck Sim cab")
                Gap(8)
                Text("GAME", style = RaceType.capLabel, color = Race.InkMuted)
                Gap(6)
                SlantTabs(games.map { it.tag() }, games.indexOf(game), { game = games[it] }, height = 40)
                Gap(8)
                Text(
                    when (game) {
                        GameId.CUSTOM -> "Starts empty. Works with any Windows game as a keyboard and gamepad."
                        GameId.BEAMNG -> "Starts empty, with live vehicle state and Bind in game when the BeamNG mod is installed. To start from the full layout, duplicate the built-in BeamNG profile."
                        GameId.MUDRUNNER -> "Starts empty. To start from the full layout, duplicate the built-in MudRunner profile."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Race.InkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), game) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
