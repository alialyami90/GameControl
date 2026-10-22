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
import com.gamecontroller.ui.icons.IconLibrary
import com.gamecontroller.ui.theme.Rig
import com.gamecontroller.ui.theme.RigMotion
import com.gamecontroller.ui.theme.RigType

/**
 * Icon, label and optional readout printed on a cap. Scales the icon with the cap and drops
 * the label on caps too small to print it legibly (it stays in the accessibility label).
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
    val ink by animateColorAsState(if (lit) accent else Rig.Bone, RigMotion.settle(), label = "ink")
    val labelInk by animateColorAsState(if (lit) Rig.Bone else Rig.BoneMuted, RigMotion.settle(), label = "labelInk")
    BoxWithConstraints(modifier.fillMaxSize().padding(top = LatchBandHeight, start = 6.dp, end = 6.dp, bottom = 6.dp)) {
        val minSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val wide = maxWidth >= 120.dp
        val showLabel = maxHeight >= 64.dp && label.isNotBlank()
        val vector = IconLibrary[icon]
        val iconSize = (minSide * if (showLabel) 0.42f else 0.56f).coerceIn(20.dp, 56.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                readout != null -> Text(
                    readout,
                    style = if (minSide >= 96.dp) RigType.readoutLarge else RigType.readout,
                    color = ink,
                    maxLines = 1,
                )
                vector != null -> Icon(vector, contentDescription = null, tint = ink, modifier = Modifier.size(iconSize))
                else -> Text(
                    label.uppercase(),
                    style = if (minSide >= 96.dp) RigType.capLabelLarge else RigType.capLabel,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showLabel && (vector != null || readout != null)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    label.uppercase(),
                    style = if (wide) RigType.capLabelLarge else RigType.capLabel,
                    color = labelInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
