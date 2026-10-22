package com.gamecontroller.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.OnAccent
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType

/** A raised panel on the ground: the container for each work area. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(CapShape(cut = 18.dp, radius = 8.dp))
            .background(Race.Panel)
            .border(1.dp, Race.Line, CapShape(cut = 18.dp, radius = 8.dp))
            .padding(20.dp),
        content = content,
    )
}

/** Section heading inside panels and the inspector: small caps with an electric tick. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 10.dp, height = 3.dp).background(Race.Electric))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = RaceType.capLabel, color = Race.InkMuted, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun raceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Race.Electric,
    unfocusedBorderColor = Race.LineStrong,
    focusedLabelColor = Race.Electric,
    unfocusedLabelColor = Race.InkMuted,
    cursorColor = Race.Electric,
    focusedContainerColor = Race.Ground,
    unfocusedContainerColor = Race.Ground,
    errorBorderColor = Race.Red,
    errorLabelColor = Race.Red,
    errorSupportingTextColor = Race.Red,
)

/** Single-line text field in the house style. [error] replaces the hint when set. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    number: Boolean = false,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum", letterSpacing = androidx.compose.ui.unit.TextUnit(0.02f, androidx.compose.ui.unit.TextUnitType.Em)) else MaterialTheme.typography.bodyLarge,
        colors = raceFieldColors(),
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Integer stepper: minus, value, plus. Targets are 48dp. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", tint = if (value > range.first) Race.Ink else Race.InkFaint)
        }
        Text(
            value.toString(),
            style = RaceType.readout,
            color = Race.Ink,
            modifier = Modifier.width(40.dp).semantics { contentDescription = "$label $value" },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label", tint = if (value < range.last) Race.Ink else Race.InkFaint)
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Race.Ink)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Race.InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Race.Electric,
                checkedThumbColor = OnAccent,
                uncheckedTrackColor = Race.Ground,
                uncheckedBorderColor = Race.LineStrong,
                uncheckedThumbColor = Race.InkMuted,
            ),
        )
    }
}

/** Round colour swatch used by the accent picker. */
@Composable
fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, if (selected) Race.Ink else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Wrapping row of selectable chips (slanted plates) for short option lists. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            // 48dp touch target around a 40dp slanted chip.
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; this.selected = on }
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(com.gamecontroller.ui.theme.SlantShape())
                        .background(if (on) Race.Electric else Race.Cap)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o, style = RaceType.capLabel, color = if (on) OnAccent else Race.Ink)
                }
            }
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Race.InkMuted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier.padding(top = 4.dp))
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))
