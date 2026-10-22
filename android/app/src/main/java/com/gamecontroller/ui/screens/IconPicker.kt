package com.gamecontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamecontroller.ui.components.SlantTabs
import com.gamecontroller.ui.components.raceFieldColors
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType

/** Icon library: search, filter by category, tap to pick. */
@Composable
fun IconPicker(selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val tabs = listOf("All") + IconLibrary.categories
    var tab by remember { mutableStateOf(0) }
    val shown = IconLibrary.search(query).filter { tab == 0 || it.category == tabs[tab] }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(CapShape(cut = 22.dp, radius = 10.dp))
                .background(Race.PanelRaised)
                .border(1.dp, Race.Line, CapShape(cut = 22.dp, radius = 10.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ICONS", style = RaceType.plate, color = Race.Ink)
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search, e.g. diff, lights, camera") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Race.InkMuted) },
                    singleLine = true,
                    colors = raceFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Race.InkMuted) }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                SlantTabs(tabs, tab, { tab = it })
            }
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No icons match \"$query\".", style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(shown, key = { it.id }) { e ->
                        val on = e.id == selected
                        Column(
                            Modifier
                                .aspectRatio(1f)
                                .clip(CapShape(cut = 10.dp, radius = 4.dp))
                                .background(if (on) Race.Electric.copy(alpha = 0.16f) else Race.Cap)
                                .border(if (on) 1.5.dp else 1.dp, if (on) Race.Electric else Race.Line, CapShape(cut = 10.dp, radius = 4.dp))
                                .clickable { onPick(e.id) }
                                .semantics(mergeDescendants = true) { role = Role.Button; this.selected = on; contentDescription = e.name }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(e.vector, contentDescription = null, tint = if (on) Race.Electric else Race.Ink, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                e.name.uppercase(),
                                style = RaceType.unit,
                                color = if (on) Race.Ink else Race.InkMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
