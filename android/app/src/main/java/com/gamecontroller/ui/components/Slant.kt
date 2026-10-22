package com.gamecontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.gamecontroller.ui.theme.OnAccent
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape

enum class SlantStyle { PRIMARY, OUTLINE, QUIET }

/** The racing-menu button: a slanted plate with italic caps label. 48dp tall target. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlantButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: SlantStyle = SlantStyle.OUTLINE,
    enabled: Boolean = true,
    accent: Color = Race.Electric,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val shape = SlantShape()
    val fg = when {
        style == SlantStyle.PRIMARY -> OnAccent
        style == SlantStyle.OUTLINE -> accent
        else -> Race.Ink
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .clip(shape)
                .then(
                    when (style) {
                        SlantStyle.PRIMARY -> Modifier.background(accent)
                        SlantStyle.OUTLINE -> Modifier.background(Race.Panel).border(1.5.dp, accent, shape)
                        SlantStyle.QUIET -> Modifier.background(Race.Cap)
                    }
                )
                .combinedClickable(enabled = enabled, onLongClick = onLongClick, onLongClickLabel = longClickLabel, onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text.uppercase(), style = RaceType.capLabelLarge.copy(fontStyle = FontStyle.Italic), color = fg, maxLines = 1)
        }
    }
}

/** A row of slanted plates, one selected, used for tabs and small choices. */
@Composable
fun SlantTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 36,
) {
    Row(modifier.height(height.dp), horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        labels.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(SlantShape())
                    .background(if (on) Race.Electric else Race.Panel)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.uppercase(),
                    style = RaceType.capLabel.copy(fontStyle = FontStyle.Italic),
                    color = if (on) OnAccent else Race.InkMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small slanted tag that states a mode or type inline, beside a title rather than above it. */
@Composable
fun StateTag(text: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    Box(
        modifier
            .height(22.dp)
            .clip(SlantShape())
            .background(if (quiet) Race.CapHigh else Race.Electric)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = RaceType.unit.copy(fontStyle = FontStyle.Italic),
            color = if (quiet) Race.Ink else OnAccent,
            maxLines = 1,
        )
    }
}
