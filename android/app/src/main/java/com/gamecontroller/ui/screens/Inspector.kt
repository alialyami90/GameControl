package com.gamecontroller.ui.screens

import com.gamecontroller.ui.components.KeyField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.Accent
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.GameId
import com.gamecontroller.model.Page
import com.gamecontroller.model.Position
import com.gamecontroller.ui.components.ChoiceChips
import com.gamecontroller.ui.components.Field
import com.gamecontroller.ui.components.Gap
import com.gamecontroller.ui.components.Hint
import com.gamecontroller.ui.components.SectionHeader
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantStyle
import com.gamecontroller.ui.components.StateTag
import com.gamecontroller.ui.components.Stepper
import com.gamecontroller.ui.components.Swatch
import com.gamecontroller.ui.components.ToggleRow
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.displayName
import com.gamecontroller.ui.theme.lit

/** Everything about one control, top to bottom in the order you set it up. */
@Composable
fun Inspector(
    control: Control,
    page: Page,
    game: GameId,
    beamngCommands: List<String>,
    onUpdate: (Control) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    stateKeys: List<String> = emptyList(),
) {
    var picking by remember { mutableStateOf(false) }
    val c = control
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.label.ifBlank { "Untitled" },
                style = MaterialTheme.typography.headlineSmall,
                color = Race.Ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            StateTag(c.type.displayName(), quiet = true)
        }
        IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ${c.label}", tint = Race.Red) }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close inspector", tint = Race.InkMuted) }
    }

    SectionHeader("Look")
    Field(c.label, { onUpdate(c.copy(label = it)) }, "Label")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CapShape(cut = 10.dp, radius = 4.dp))
                .background(Race.Cap)
                .border(1.dp, Race.Line, CapShape(cut = 10.dp, radius = 4.dp))
                .clickable { picking = true }
                .semantics { role = Role.Button; contentDescription = "Change icon" },
            contentAlignment = Alignment.Center,
        ) {
            val v = IconLibrary[c.icon]
            if (v != null) Icon(v, contentDescription = null, tint = c.accent.lit(), modifier = Modifier.size(32.dp))
            else Text("NONE", style = RaceType.unit, color = Race.InkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(IconLibrary.entry(c.icon)?.name ?: "No icon", style = MaterialTheme.typography.bodyLarge, color = Race.Ink)
            Row {
                SlantButton("Change", { picking = true }, style = SlantStyle.QUIET)
                if (c.icon != null) SlantButton("Clear", { onUpdate(c.copy(icon = null)) }, style = SlantStyle.QUIET)
            }
        }
    }
    Gap(8)
    Text("LIGHTS UP IN", style = RaceType.unit, color = Race.InkFaint)
    Row {
        Accent.entries.forEach { a -> Swatch(a.lit(), a == c.accent, a.displayName()) { onUpdate(c.copy(accent = a)) } }
    }
    Stepper("Width", c.w, widthRange(c, page), { onResize(c.id, it, c.h) })
    Stepper("Height", c.h, heightRange(c, page), { onResize(c.id, c.w, it) })

    when (c.type) {
        ControlType.MOMENTARY, ControlType.LATCHING -> {
            SectionHeader(if (c.type == ControlType.MOMENTARY) "While held" else "When on")
            ActionEditor(c.action, { onUpdate(c.copy(action = it)) }, beamngCommands)
        }
        ControlType.SLIDER -> {
            SectionHeader("Axis")
            ActionEditor(c.action, { onUpdate(c.copy(action = it)) }, beamngCommands, axisOnly = true)
            Gap(10)
            Text("Range", style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted)
            ChoiceChips(listOf("0 to 1", "-1 to 1"), if (c.range == AxisRange.UNIPOLAR) "0 to 1" else "-1 to 1", {
                onUpdate(c.copy(range = if (it == "0 to 1") AxisRange.UNIPOLAR else AxisRange.BIPOLAR))
            })
            ToggleRow("Spring back on release", c.springReturn, { onUpdate(c.copy(springReturn = it)) }, hint = "Returns to rest like a throttle pedal")
            ToggleRow("Vertical", c.vertical, { onUpdate(c.copy(vertical = it)) })
        }
        ControlType.JOYSTICK -> {
            SectionHeader("Left-right")
            ActionEditor(c.action, { onUpdate(c.copy(action = it)) }, beamngCommands, axisOnly = true)
            SectionHeader("Forward-back")
            ActionEditor(c.actionY, { onUpdate(c.copy(actionY = it)) }, beamngCommands, axisOnly = true)
            Hint("Both axes run -1 to 1 and spring back to centre on release. Forward and right are +1.")
        }
        ControlType.ROTARY -> {
            SectionHeader("Turn right")
            ActionEditor(c.cw, { onUpdate(c.copy(cw = it)) }, beamngCommands)
            SectionHeader("Turn left")
            ActionEditor(c.ccw, { onUpdate(c.copy(ccw = it)) }, beamngCommands)
        }
        ControlType.SWITCH, ControlType.RADIO -> {
            SectionHeader("Positions", trailing = {
                IconButton(onClick = {
                    onUpdate(c.copy(positions = c.positions + Position("Pos ${c.positions.size + 1}")))
                }) { Icon(Icons.Rounded.Add, contentDescription = "Add position", tint = Race.Ink) }
            })
            if (c.positions.isEmpty()) Hint("Add at least two positions.")
            c.positions.forEachIndexed { i, p ->
                PositionRow(i, p, c.positions.size, beamngCommands,
                    onChange = { np -> onUpdate(c.copy(positions = c.positions.toMutableList().also { it[i] = np })) },
                    onRemove = { onUpdate(c.copy(positions = c.positions.filterIndexed { j, _ -> j != i })) })
            }
        }
    }

    SectionHeader("Game link")
    Hint("Optional. Lets the game drive this control's light and availability through the BeamNG mod or telemetry.")
    Gap(4)
    if (c.type == ControlType.SWITCH || c.type == ControlType.RADIO) {
        KeyField(c.positionKey.orEmpty(), { onUpdate(c.copy(positionKey = it.trim().ifBlank { null })) }, "Position state key", stateKeys, "gearbox.mode")
    } else {
        KeyField(c.feedbackKey.orEmpty(), { onUpdate(c.copy(feedbackKey = it.trim().ifBlank { null })) }, "Light state key", stateKeys, "lights.low")
    }
    KeyField(c.visibleWhen.orEmpty(), { onUpdate(c.copy(visibleWhen = it.trim().ifBlank { null })) }, "Only available when", stateKeys.filter { it.startsWith("has.") }.ifEmpty { stateKeys }, "has.diff.front")
    if (game == GameId.BEAMNG && c.action is Action.VJoyButton) {
        Field(c.bindTo.orEmpty(), { onUpdate(c.copy(bindTo = it.trim().ifBlank { null })) }, "Bind in game to", hint = "BeamNG action name, used by Bind in game", mono = true)
    }
    Gap(24)

    if (picking) {
        IconPicker(c.icon, onPick = { onUpdate(c.copy(icon = it)); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
private fun PositionRow(
    i: Int,
    p: Position,
    count: Int,
    beamngCommands: List<String>,
    onChange: (Position) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CapShape(cut = 8.dp, radius = 4.dp))
            .background(Race.Ground)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = RaceType.readout, color = Race.Electric, modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f).clickable { open = !open }.padding(vertical = 6.dp)) {
                Text(p.label.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, color = Race.Ink)
                Text(p.action.summary(), style = MaterialTheme.typography.bodySmall, color = Race.InkMuted, maxLines = 1)
            }
            IconButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Collapse" else "Expand", tint = Race.InkMuted)
            }
            IconButton(onClick = onRemove, enabled = count > 1) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove position ${i + 1}", tint = Race.InkMuted)
            }
        }
        if (open) {
            Field(p.label, { onChange(p.copy(label = it)) }, "Label")
            Field(p.stateValue.orEmpty(), { onChange(p.copy(stateValue = it.trim().ifBlank { null })) }, "Game value", hint = "Value of the position state key, e.g. D", mono = true)
            ActionEditor(p.action, { onChange(p.copy(action = it)) }, beamngCommands)
        }
    }
}
