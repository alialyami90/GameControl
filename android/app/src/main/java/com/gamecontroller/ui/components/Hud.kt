package com.gamecontroller.ui.components

import androidx.compose.foundation.layout.heightIn
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
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.OnAccent
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape
import kotlin.math.roundToInt

enum class LinkTone { OK, BUSY, PROBLEM, OFF }

fun LinkTone.color(): Color = when (this) {
    LinkTone.OK -> Race.Green
    LinkTone.BUSY -> Race.Amber
    LinkTone.PROBLEM -> Race.Red
    LinkTone.OFF -> Race.InkFaint
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
fun LinkBadge(
    icon: ImageVector,
    name: String,
    summary: LinkSummary,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    lampOnly: Boolean = false,
    alert: Boolean = false,
) {
    // alert: a press was just dropped because nothing could carry it. The badge goes red and says so.
    val shown = if (alert) LinkSummary(LinkTone.PROBLEM, "No link", "Press not sent: no link") else summary
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(SlantShape())
            .background(Race.Panel)
            .border(if (alert) 1.5.dp else 1.dp, if (alert) Race.Red else Race.Line, SlantShape())
            .padding(horizontal = if (lampOnly) 12.dp else 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = shown.long },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (shown.tone == LinkTone.OFF) Race.InkFaint else Race.Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        // Lamp-only (narrowest phones): the lamp's fill still says linked / connecting / off.
        StatusLamp(shown.tone.color(), lit = shown.tone != LinkTone.OFF, half = shown.tone == LinkTone.BUSY, size = if (lampOnly) 10 else 8)
        if (lampOnly) return@Row
        Spacer(Modifier.width(6.dp))
        if (!compact) {
            Text(name, style = RaceType.unit, color = Race.InkMuted)
            Spacer(Modifier.width(4.dp))
        }
        Text(shown.short.uppercase(), style = RaceType.unit, color = when { alert -> Race.Red; shown.tone == LinkTone.OFF -> Race.InkFaint; else -> Race.Ink }, maxLines = 1)
    }
}

/**
 * Status lamp. Shape carries the state as well as colour: filled is linked, half-filled is
 * working on it, hollow is off.
 */
@Composable
fun StatusLamp(color: Color, lit: Boolean, modifier: Modifier = Modifier, size: Int = 8, half: Boolean = false) {
    androidx.compose.foundation.Canvas(modifier.size(size.dp)) {
        val r = this.size.minDimension / 2
        val sw = 1.5.dp.toPx()
        if (lit && half) {
            drawArc(color, 90f, 180f, useCenter = true)
        } else if (lit) {
            drawCircle(color, r)
        }
        drawCircle(color, r - sw / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
    }
}

@Composable
fun BluetoothLinkBadge(hid: HidUi, modifier: Modifier = Modifier, compact: Boolean = false, lampOnly: Boolean = false, alert: Boolean = false) = LinkBadge(
    if (hid.status == HidStatus.UNSUPPORTED) Icons.Rounded.BluetoothDisabled else Icons.Rounded.Bluetooth,
    "BT", hid.summary(), modifier, compact, lampOnly, alert,
)

@Composable
fun PcLinkBadge(connection: ConnectionUi, modifier: Modifier = Modifier, compact: Boolean = false, lampOnly: Boolean = false, alert: Boolean = false) =
    LinkBadge(Icons.Rounded.Computer, "PC", connection.summary(), modifier, compact, lampOnly, alert)

/**
 * Game readouts from telemetry. Draws nothing for games that report no state. Speed arrives in
 * m/s and shows in km/h or, with [imperial], mph. [showRpm] false keeps only speed and gear.
 */
@Composable
fun GameReadouts(game: Map<String, Any>, modifier: Modifier = Modifier, imperial: Boolean = false, showRpm: Boolean = true) {
    val gear = game["gear"]?.toString()
    val speed = (game["speed"] as? Number)?.toDouble()?.let { (it * if (imperial) 2.23694 else 3.6).roundToInt() }
    val rpm = (game["rpm"] as? Number)?.toInt()?.takeIf { showRpm }
    if (gear == null && speed == null && rpm == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (speed != null) Readout(speed.toString(), if (imperial) "MPH" else "KM/H", Modifier.widthIn(min = 44.dp))
        if (rpm != null) Readout("%,d".format(rpm), "RPM", Modifier.widthIn(min = 56.dp))
        if (gear != null) {
            Box(
                Modifier
                    .size(width = 52.dp, height = 40.dp)
                    .clip(SlantShape())
                    .background(Race.Electric)
                    .semantics { contentDescription = "Gear $gear" },
                contentAlignment = Alignment.Center,
            ) {
                Text(gear, style = RaceType.readoutLarge.copy(fontSize = RaceType.readout.fontSize * 1.3f), color = OnAccent, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun Readout(value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.End) {
        Text(value, style = RaceType.readout, color = Race.Ink, maxLines = 1)
        Text(unit, style = RaceType.unit, color = Race.InkFaint)
    }
}
