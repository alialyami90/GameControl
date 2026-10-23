package com.gamecontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.unit.dp
import com.gamecontroller.hid.HidKeys
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.KeyTrigger
import com.gamecontroller.model.MacroStep
import com.gamecontroller.ui.components.ChoiceChips
import com.gamecontroller.ui.components.Field
import com.gamecontroller.ui.components.Gap
import com.gamecontroller.ui.components.Hint
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantStyle
import com.gamecontroller.ui.components.Stepper
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType

private enum class ActionKind(val label: String) {
    NONE("Nothing"),
    KEY("Key or key combo"),
    GAMEPAD_BUTTON("Gamepad button"),
    GAMEPAD_AXIS("Gamepad axis"),
    MACRO("Macro"),
    BEAMNG("BeamNG command"),
    XBOX_BUTTON("Xbox button"),
    XBOX_AXIS("Xbox axis"),
}

private fun Action.kind(): ActionKind = when (this) {
    Action.None -> ActionKind.NONE
    is Action.Key -> ActionKind.KEY
    is Action.VJoyButton -> ActionKind.GAMEPAD_BUTTON
    is Action.VJoyAxis -> ActionKind.GAMEPAD_AXIS
    is Action.Macro -> ActionKind.MACRO
    is Action.BeamNG -> ActionKind.BEAMNG
    is Action.XInputButton -> ActionKind.XBOX_BUTTON
    is Action.XInputAxis -> ActionKind.XBOX_AXIS
}

private fun ActionKind.blank(): Action = when (this) {
    ActionKind.NONE -> Action.None
    ActionKind.KEY -> Action.Key(emptyList())
    ActionKind.GAMEPAD_BUTTON -> Action.VJoyButton(button = 1)
    ActionKind.GAMEPAD_AXIS -> Action.VJoyAxis(axis = "X")
    ActionKind.MACRO -> Action.Macro(listOf(MacroStep()))
    ActionKind.BEAMNG -> Action.BeamNG("")
    ActionKind.XBOX_BUTTON -> Action.XInputButton("A")
    ActionKind.XBOX_AXIS -> Action.XInputAxis("LT")
}

/** One-line summary used on collapsed rows, e.g. "Key LCTRL + H (tap)". */
fun Action.summary(): String = when (this) {
    Action.None -> "Does nothing"
    is Action.Key -> if (keys.isEmpty()) "Key: not set" else "Key ${keys.joinToString(" + ")} (${trigger.name.lowercase()})"
    is Action.VJoyButton -> "Gamepad button $button" + if (device != 1) " on device $device" else ""
    is Action.VJoyAxis -> "Gamepad axis $axis"
    is Action.Macro -> "Macro, ${steps.size} steps"
    is Action.BeamNG -> if (command.isBlank()) "BeamNG command: not set" else "BeamNG $command"
    is Action.XInputButton -> "Xbox $button"
    is Action.XInputAxis -> "Xbox axis $axis"
}

private val GamepadAxes = listOf("X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1")
private val XboxButtons = listOf("A", "B", "X", "Y", "LB", "RB", "BACK", "START", "LS", "RS", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "GUIDE")
private val XboxAxes = listOf("LX", "LY", "RX", "RY", "LT", "RT")

/** Parses "lctrl + h" into validated key names, or returns the first unknown token. */
private fun parseKeys(text: String): Pair<List<String>, String?> {
    val tokens = text.split('+', ',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    val known = HidKeys.ALL_NAMES.toSet()
    val bad = tokens.firstOrNull { it !in known }
    return tokens to bad
}

/**
 * Edits any [Action]. [axisOnly] narrows the choice for sliders; [beamngCommands] are the
 * commands the mod reported, offered as suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionEditor(
    action: Action,
    onChange: (Action) -> Unit,
    beamngCommands: List<String>,
    modifier: Modifier = Modifier,
    axisOnly: Boolean = false,
) {
    val kinds = if (axisOnly) listOf(ActionKind.NONE, ActionKind.GAMEPAD_AXIS, ActionKind.XBOX_AXIS)
    else ActionKind.entries.filter { it != ActionKind.GAMEPAD_AXIS && it != ActionKind.XBOX_AXIS }
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sends", style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { menu = true }) {
                    Text(action.kind().label.uppercase(), style = RaceType.capLabel, color = Race.Electric)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    kinds.forEach { k ->
                        DropdownMenuItem(text = { Text(k.label) }, onClick = { menu = false; if (k != action.kind()) onChange(k.blank()) })
                    }
                }
            }
        }
        when (action) {
            Action.None -> Hint("Pick what this control sends to the PC.")
            is Action.Key -> {
                var text by remember(action) { mutableStateOf(action.keys.joinToString(" + ")) }
                val (keys, bad) = parseKeys(text)
                Field(
                    text,
                    { text = it; val (k, b) = parseKeys(it); if (b == null) onChange(action.copy(keys = k)) },
                    "Keys",
                    hint = "Modifiers first, e.g. LCTRL + H, F5, NUMPAD_ADD",
                    error = bad?.let { "Unknown key \"$it\"" } ?: if (keys.isEmpty() && text.isNotBlank()) "Add at least one key" else null,
                    mono = true,
                )
                Gap(4)
                Text("WHEN", style = RaceType.unit, color = Race.InkFaint)
                Gap(4)
                ChoiceChips(listOf("Hold", "Tap", "Press"), action.trigger.name.lowercase().replaceFirstChar { it.uppercase() }, {
                    onChange(action.copy(trigger = KeyTrigger.valueOf(it.uppercase())))
                })
                Hint(
                    when (action.trigger) {
                        KeyTrigger.HOLD -> "Keys stay down while the control is pressed or on."
                        KeyTrigger.TAP -> "Taps the keys on every change. Best for toggles the game flips itself."
                        KeyTrigger.PRESS -> "Taps the keys once when the control turns on."
                    },
                )
            }
            is Action.VJoyButton -> {
                Stepper("Button", action.button, 1..128, { onChange(action.copy(button = it)) })
                Stepper("Device", action.device, 1..16, { onChange(action.copy(device = it)) })
                Hint("Buttons 1 to 64 on device 1 also work over Bluetooth with no PC software. Higher buttons need vJoy on the PC.")
            }
            is Action.VJoyAxis -> {
                ChoiceChips(GamepadAxes, action.axis, { onChange(action.copy(axis = it)) })
                // Sliders and joysticks (axisOnly) set the range on the control itself; core ignores the action's.
                if (!axisOnly) {
                    Gap(8)
                    ChoiceChips(listOf("0 to 1", "-1 to 1"), if (action.range == AxisRange.UNIPOLAR) "0 to 1" else "-1 to 1", {
                        onChange(action.copy(range = if (it == "0 to 1") AxisRange.UNIPOLAR else AxisRange.BIPOLAR))
                    })
                }
                Hint("Gamepad axes reach Windows over Bluetooth or through vJoy.")
            }
            is Action.Macro -> MacroEditor(action, onChange)
            is Action.BeamNG -> {
                Field(action.command, { onChange(action.copy(command = it.trim())) }, "Command", hint = "e.g. lights.low.toggle", mono = true)
                val matches = beamngCommands.filter { action.command.isBlank() || action.command in it }.take(8)
                if (matches.isNotEmpty()) {
                    Gap(4)
                    ChoiceChips(matches, action.command, { onChange(action.copy(command = it)) })
                } else if (beamngCommands.isEmpty()) {
                    Hint("Connect to the PC with the BeamNG mod running to see the full command list.")
                }
            }
            is Action.XInputButton -> {
                ChoiceChips(XboxButtons, action.button, { onChange(action.copy(button = it)) })
                Hint("Needs the PC companion with the virtual Xbox pad. For games that only read Xbox controllers.")
            }
            is Action.XInputAxis -> {
                ChoiceChips(XboxAxes, action.axis, { onChange(action.copy(axis = it)) })
                Hint("Needs the PC companion with the virtual Xbox pad.")
            }
        }
    }
}

@Composable
private fun MacroEditor(action: Action.Macro, onChange: (Action) -> Unit) {
    fun update(i: Int, s: MacroStep) = onChange(action.copy(steps = action.steps.toMutableList().also { it[i] = s }))
    action.steps.forEachIndexed { i, step ->
        Column(
            Modifier
                .padding(vertical = 4.dp)
                .clip(CapShape(cut = 8.dp, radius = 4.dp))
                .background(Race.Ground)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STEP ${i + 1}", style = RaceType.unit, color = Race.InkFaint, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(action.copy(steps = action.steps.filterIndexed { j, _ -> j != i })) }, enabled = action.steps.size > 1) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove step ${i + 1}", tint = Race.InkMuted)
                }
            }
            var text by remember(step) { mutableStateOf(step.keys.joinToString(" + ")) }
            val (_, bad) = parseKeys(text)
            Field(text, { text = it; val (k, b) = parseKeys(it); if (b == null) update(i, step.copy(keys = k)) }, "Keys", error = bad?.let { "Unknown key \"$it\"" }, mono = true)
            Row {
                Field(step.holdMs.toString(), { v -> update(i, step.copy(holdMs = v.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0)) }, "Hold ms", number = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Field(step.delayMs.toString(), { v -> update(i, step.copy(delayMs = v.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0)) }, "Then wait ms", number = true, modifier = Modifier.weight(1f))
            }
        }
    }
    SlantButton("Add step", { onChange(action.copy(steps = action.steps + MacroStep(holdMs = 50))) }, icon = Icons.Rounded.Add, style = SlantStyle.QUIET)
}
