package com.gamecontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceMotion
import com.gamecontroller.ui.theme.RaceType

/**
 * Icon, label and optional readout printed on a cap. Scales the icon with the cap and drops
 * the label below 48dp of face height, where it can't be printed legibly (it stays in the accessibility label).
 */
@Composable
fun CapFace(
    label: String,
    icon: String?,
    lit: Boolean,
    accent: Color,
    readout: String? = null,
    modifier: Modifier = Modifier,
) {
    val ink by animateColorAsState(if (lit) accent else Race.Ink, RaceMotion.settle(), label = "ink")
    val labelInk by animateColorAsState(if (lit) Race.Ink else Race.InkMuted, RaceMotion.settle(), label = "labelInk")
    BoxWithConstraints(modifier.fillMaxSize().padding(top = LatchBandHeight, start = 6.dp, end = 6.dp, bottom = 6.dp)) {
        val minSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val wide = maxWidth >= 120.dp
        // Small caps (48-64dp inside) keep the label in the compact unit style rather than dropping it.
        val showLabel = maxHeight >= 48.dp && label.isNotBlank()
        // Narrow caps also take the compact style, so labels like IGNITION fit instead of truncating.
        val small = maxHeight < 64.dp || maxWidth < 76.dp
        val vector = IconLibrary[icon]
        val iconSize = (minSide * if (showLabel) (if (small) 0.36f else 0.42f) else 0.56f).coerceIn(20.dp, 56.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                readout != null -> Text(
                    readout,
                    style = if (minSide >= 96.dp) RaceType.readoutLarge else RaceType.readout,
                    color = ink,
                    maxLines = 1,
                )
                vector != null -> Icon(vector, contentDescription = null, tint = ink, modifier = Modifier.size(iconSize))
                else -> Text(
                    label.uppercase(),
                    style = if (minSide >= 96.dp) RaceType.capLabelLarge else RaceType.capLabel,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showLabel && (vector != null || readout != null)) {
                Spacer(Modifier.height(if (small) 2.dp else 6.dp))
                Text(
                    label.uppercase(),
                    style = when {
                        small -> RaceType.unit.copy(lineHeight = 12.sp, letterSpacing = 0.04.em)
                        wide -> RaceType.capLabelLarge
                        else -> RaceType.capLabel
                    },
                    color = labelInk,
                    textAlign = TextAlign.Center,
                    // Small caps wrap to a second line instead of truncating (SHIFT DOWN, CENTER DIFF).
                    maxLines = if (small) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
