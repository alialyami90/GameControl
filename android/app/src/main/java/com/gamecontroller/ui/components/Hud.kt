package com.gamecontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.ConnectionStatus
import com.gamecontroller.model.ConnectionUi
import com.gamecontroller.model.HidStatus
import com.gamecontroller.model.HidUi
import com.gamecontroller.ui.theme.Rig
import com.gamecontroller.ui.theme.RigType
import kotlin.math.roundToInt

enum class LinkTone { OK, BUSY, PROBLEM, OFF }

fun LinkTone.color(): Color = when (this) {
    LinkTone.OK -> Rig.Green
    LinkTone.BUSY -> Rig.Yellow
    LinkTone.PROBLEM -> Rig.Red
    LinkTone.OFF -> Rig.BoneFaint
}

data class LinkSummary(val tone: LinkTone, val short: String, val long: String)

fun ConnectionUi.summary(): LinkSummary = when (status) {
    ConnectionStatus.CONNECTED -> LinkSummary(LinkTone.OK, latencyMs?.let { "$it ms" } ?: "Linked", "PC companion linked" + (serverName?.let { " to $it" } ?: ""))
    ConnectionStatus.SEARCHING -> LinkSummary(LinkTone.BUSY, "Searching", "Looking for the PC companion")
    ConnectionStatus.CONNECTING -> LinkSummary(LinkTone.BUSY, "Connecting", "Connecting to the PC companion")
    ConnectionStatus.PAIRING -> LinkSummary(LinkTone.BUSY, "PIN needed", "Enter the PIN shown on the PC")
    ConnectionStatus.ERROR -> LinkSummary(LinkTone.PROBLEM, "Error", message ?: "PC companion link failed")
    ConnectionStatus.DISCONNECTED -> LinkSummary(LinkTone.OFF, "Off", "PC companion not linked")
}

fun HidUi.summary(): LinkSummary = when (status) {
    HidStatus.CONNECTED -> LinkSummary(LinkTone.OK, "Paired", "Bluetooth gamepad connected" + (hostName?.let { " to $it" } ?: ""))
    HidStatus.READY -> LinkSummary(LinkTone.BUSY, "Ready", "Bluetooth gamepad ready, not connected to a PC")
    HidStatus.REGISTERING -> LinkSummary(LinkTone.BUSY, "Starting", "Starting the Bluetooth gamepad")
    HidStatus.OFF -> LinkSummary(LinkTone.OFF, "Off", "Bluetooth gamepad off")
    HidStatus.UNSUPPORTED -> LinkSummary(LinkTone.OFF, "N/A", "This phone can't act as a Bluetooth gamepad")
}

/** Compact link badge: icon, status lamp, short status. */
@Composable
fun LinkBadge(icon: ImageVector, name: String, summary: LinkSummary, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Rig.Case)
            .border(1.dp, Rig.Stitch, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary.long },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (summary.tone == LinkTone.OFF) Rig.BoneFaint else Rig.Bone, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        StatusLamp(summary.tone.color(), lit = summary.tone != LinkTone.OFF)
        Spacer(Modifier.width(6.dp))
        Text(name, style = RigType.unit, color = Rig.BoneMuted)
        Spacer(Modifier.width(4.dp))
        Text(summary.short.uppercase(), style = RigType.unit, color = if (summary.tone == LinkTone.OFF) Rig.BoneFaint else Rig.Bone, maxLines = 1)
    }
}

@Composable
fun StatusLamp(color: Color, lit: Boolean, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (lit) color else Color.Transparent)
            .border(1.5.dp, color, CircleShape),
    )
}

@Composable
fun BluetoothLinkBadge(hid: HidUi, modifier: Modifier = Modifier) = LinkBadge(
    if (hid.status == HidStatus.UNSUPPORTED) Icons.Rounded.BluetoothDisabled else Icons.Rounded.Bluetooth,
    "BT", hid.summary(), modifier,
)

@Composable
fun PcLinkBadge(connection: ConnectionUi, modifier: Modifier = Modifier) =
    LinkBadge(Icons.Rounded.Computer, "PC", connection.summary(), modifier)

/** Game readouts from telemetry. Draws nothing for games that report no state. */
@Composable
fun GameReadouts(game: Map<String, Any>, modifier: Modifier = Modifier) {
    val gear = game["gear"]?.toString()
    val speed = (game["speed"] as? Number)?.toDouble()?.let { (it * 3.6).roundToInt() }
    val rpm = (game["rpm"] as? Number)?.toInt()
    if (gear == null && speed == null && rpm == null) return
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (speed != null) Readout(speed.toString(), "KM/H", Modifier.widthIn(min = 44.dp))
        if (rpm != null) Readout("%,d".format(rpm), "RPM", Modifier.widthIn(min = 56.dp))
        if (gear != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Rig.Case)
                    .border(1.5.dp, Rig.HiVis, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = "Gear $gear" },
                contentAlignment = Alignment.Center,
            ) {
                Text(gear, style = RigType.readoutLarge.copy(fontSize = RigType.readout.fontSize * 1.25f), color = Rig.Bone, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun Readout(value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(value, style = RigType.readout, color = Rig.Bone, maxLines = 1)
        Text(unit, style = RigType.unit, color = Rig.BoneFaint)
    }
}
