package com.gamecontroller.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/**
 * The racing-menu button: a slanted plate with italic caps label. The whole 48dp box is the
 * touch target; the 40dp plate is only the visual. Disabled reads as a faint outline, never as
 * a dimmed (pressed-looking) fill.
 */
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = when {
        !enabled -> Race.InkFaint
        style == SlantStyle.PRIMARY -> OnAccent
        style == SlantStyle.OUTLINE -> accent
        else -> Race.Ink
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .graphicsLayer { if (pressed) { scaleX = 0.97f; scaleY = 0.97f } }
                .clip(shape)
                .then(
                    when {
                        !enabled -> Modifier.background(Race.Panel).border(1.dp, Race.Line, shape)
                        style == SlantStyle.PRIMARY -> Modifier.background(if (pressed) accent.copy(alpha = 0.85f) else accent)
                        style == SlantStyle.OUTLINE -> Modifier.background(if (pressed) Race.Cap else Race.Panel).border(1.5.dp, accent, shape)
                        else -> Modifier.background(if (pressed) Race.CapHigh else Race.Cap)
                    }
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            // Empty text gives an icon-only button; the caller supplies its contentDescription.
            if (text.isNotEmpty()) Text(text.uppercase(), style = RaceType.capLabelLarge.copy(fontStyle = FontStyle.Italic), color = fg, maxLines = 1)
        }
    }
}

/**
 * A row of slanted plates, one selected, used for tabs and small choices. Each plate is [height]
 * dp to the eye but sits in a 48dp-tall touch target, because tabs are how pages change mid-drive.
 */
@Composable
fun SlantTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 36,
) {
    Row(modifier.heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(min = height.dp)
                        .clip(SlantShape())
                        .background(if (on) Race.Electric else Race.Panel)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.uppercase(),
                        style = RaceType.capLabel.copy(fontStyle = FontStyle.Italic),
                        color = if (on) OnAccent else Race.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
            }
        }
    }
}

/** Small slanted tag that states a mode or type inline, beside a title rather than above it. */
@Composable
fun StateTag(text: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    Box(
        modifier
            .heightIn(min = 22.dp)
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
