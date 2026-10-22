package com.gamecontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.gamecontroller.ui.theme.Race

/**
 * A game state key: pick from the keys the game is reporting right now, or type one. With the
 * game off the list is empty, the picker is greyed and the field says where keys come from.
 */
@Composable
fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    liveKeys: List<String>,
    example: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val matches = remember(value, liveKeys) {
        if (value.isBlank()) liveKeys else liveKeys.filter { it.contains(value, ignoreCase = true) }.ifEmpty { liveKeys }
    }
    Box(modifier) {
        Field(
            value, onValueChange, label,
            hint = if (liveKeys.isEmpty()) "e.g. $example. Start the game with the companion linked to pick from live keys."
            else "${liveKeys.size} keys live from the game",
            mono = true,
            trailing = {
                IconButton(onClick = { open = true }, enabled = liveKeys.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = "Pick $label from the game",
                        tint = if (liveKeys.isEmpty()) Race.InkFaint else Race.Ink,
                    )
                }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Race.PanelRaised)) {
            matches.take(60).forEach { k ->
                DropdownMenuItem(
                    text = {
                        Text(
                            k,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                            color = if (k == value) Race.Electric else Race.Ink,
                        )
                    },
                    onClick = { onValueChange(k); open = false },
                )
            }
        }
    }
}
