package com.gamecontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.gamecontroller.model.BtHost
import com.gamecontroller.model.Capabilities
import com.gamecontroller.model.ConnectionStatus
import com.gamecontroller.model.ConnectionUi
import com.gamecontroller.model.DiscoveredServer
import com.gamecontroller.model.HidStatus
import com.gamecontroller.model.HidUi
import com.gamecontroller.ui.components.Field
import com.gamecontroller.ui.components.Gap
import com.gamecontroller.ui.components.Hint
import com.gamecontroller.ui.components.LinkTone
import com.gamecontroller.ui.components.Panel
import com.gamecontroller.ui.components.SectionHeader
import com.gamecontroller.ui.components.SlantButton
import com.gamecontroller.ui.components.SlantStyle
import com.gamecontroller.ui.components.StatusLamp
import com.gamecontroller.ui.components.color
import com.gamecontroller.ui.components.summary
import com.gamecontroller.ui.theme.CapShape
import com.gamecontroller.ui.theme.Race
import com.gamecontroller.ui.theme.RaceType
import com.gamecontroller.ui.theme.SlantShape

/**
 * Two ways to reach Windows, side by side: the phone as a Bluetooth controller (no PC
 * software), and the PC companion over Wi-Fi (needed for vJoy, Xbox pad and BeamNG state).
 */
@Composable
fun ConnectScreen(
    connection: ConnectionUi,
    servers: List<DiscoveredServer>,
    hid: HidUi,
    onConnect: (host: String) -> Unit,
    onSubmitPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRescan: () -> Unit,
    onHidEnable: () -> Unit,
    onHidConnect: (address: String) -> Unit,
    onHidDisconnect: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Race.InkMuted) }
            Spacer(Modifier.width(4.dp))
            Text("CONNECT", style = RaceType.plate, color = Race.Ink)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val bt: @Composable (Modifier) -> Unit = { m -> BluetoothPanel(hid, onHidEnable, onHidConnect, onHidDisconnect, m) }
            val pc: @Composable (Modifier) -> Unit = { m -> CompanionPanel(connection, servers, onConnect, onSubmitPin, onDisconnect, onRescan, m) }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    bt(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                    pc(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    bt(Modifier.fillMaxWidth())
                    pc(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(icon: ImageVector, title: String, subtitle: String, tone: LinkTone, status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Race.Ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Race.Ink, modifier = Modifier.weight(1f))
    }
    Hint(subtitle)
    Gap(14)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SlantShape(lean = 10.dp))
            .background(Race.Ground)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLamp(tone.color(), lit = tone != LinkTone.OFF, size = 10)
        Spacer(Modifier.width(10.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, color = Race.Ink)
    }
}

@Composable
private fun BluetoothPanel(
    hid: HidUi,
    onEnable: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier,
) {
    val s = hid.summary()
    Panel(modifier) {
        PanelTitle(Icons.Rounded.Bluetooth, "Bluetooth controller", "No PC software needed. Windows sees the phone as a gamepad and keyboard called GameCtl Pad.", s.tone, s.long)
        hid.message?.let { Hint(it, color = Race.Amber) }
        when (hid.status) {
            HidStatus.UNSUPPORTED -> {
                Gap()
                Text(
                    "This phone's Bluetooth can't act as a controller. Use the PC companion over Wi-Fi instead.",
                    style = MaterialTheme.typography.bodyMedium, color = Race.InkMuted,
                )
            }
            HidStatus.CONNECTED -> {
                Gap(16)
                SlantButton("Disconnect", onDisconnect, style = SlantStyle.OUTLINE)
            }
            else -> {
                SectionHeader("Pair with Windows")
                Step(1, "Tap Make discoverable below.")
                Step(2, "On the PC open Settings, Bluetooth & devices, Add device, Bluetooth.")
                Step(3, "Pick GameCtl Pad. Windows adds it as a game controller.")
                Gap(14)
                SlantButton(
                    if (hid.status == HidStatus.REGISTERING) "Starting…" else "Make discoverable",
                    onEnable,
                    style = SlantStyle.PRIMARY,
                    enabled = hid.status != HidStatus.REGISTERING,
                    icon = Icons.Rounded.Bluetooth,
                )
                if (hid.bondedHosts.isNotEmpty()) {
                    SectionHeader("Paired PCs")
                    hid.bondedHosts.forEach { h -> HostRow(h.name, h.address, "Connect") { onConnect(h.address) } }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("$n", style = RaceType.readout, color = Race.Electric, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Race.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun HostRow(name: String, detail: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CapShape(cut = 10.dp, radius = 4.dp))
            .background(Race.Cap)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$action $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Race.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Race.InkFaint)
        }
        Text(action.uppercase(), style = RaceType.capLabel.copy(fontStyle = FontStyle.Italic), color = Race.Electric, modifier = Modifier.padding(horizontal = 12.dp))
    }
    Gap(8)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionPanel(
    connection: ConnectionUi,
    servers: List<DiscoveredServer>,
    onConnect: (String) -> Unit,
    onSubmitPin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier,
) {
    val s = connection.summary()
    Panel(modifier) {
        PanelTitle(Icons.Rounded.Computer, "PC companion", "Runs on the PC over Wi-Fi. Needed for vJoy, the virtual Xbox pad, and BeamNG vehicle state.", s.tone, s.long)
        connection.message?.let { Hint(it, color = Race.Red) }
        when (connection.status) {
            ConnectionStatus.CONNECTED -> {
                Gap(14)
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(connection.serverName ?: "PC", style = MaterialTheme.typography.titleLarge, color = Race.Ink)
                        connection.host?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Race.InkFaint) }
                    }
                    connection.latencyMs?.let {
                        Text("$it", style = RaceType.readoutLarge, color = Race.Ink)
                        Spacer(Modifier.width(4.dp))
                        Text("MS", style = RaceType.unit, color = Race.InkFaint, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                SectionHeader("What this PC can do")
                Capabilities(connection.capabilities)
                Gap(16)
                SlantButton("Disconnect", onDisconnect)
            }
            ConnectionStatus.PAIRING -> {
                var pin by remember { mutableStateOf("") }
                SectionHeader("Enter the PIN")
                Hint("The PC companion is showing a PIN. Type it here to pair this phone.")
                Gap(8)
                Field(pin, { v -> pin = v.filter { it.isDigit() }.take(6) }, "PIN", number = true, mono = true)
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlantButton("Pair", { onSubmitPin(pin) }, style = SlantStyle.PRIMARY, enabled = pin.length >= 4)
                    SlantButton("Cancel", onDisconnect, style = SlantStyle.QUIET)
                }
            }
            else -> {
                var manual by remember { mutableStateOf("") }
                SectionHeader("On this network", trailing = {
                    IconButton(onClick = onRescan) { Icon(Icons.Rounded.Refresh, contentDescription = "Search again", tint = Race.InkMuted) }
                })
                if (servers.isEmpty()) {
                    Text(
                        if (connection.status == ConnectionStatus.SEARCHING) "Searching for PCs running the companion…"
                        else "No PCs found. Check the companion is running and the phone is on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Race.InkMuted,
                    )
                } else {
                    servers.forEach { srv -> HostRow(srv.name, srv.host, "Connect") { onConnect(srv.host) } }
                }
                SectionHeader("Or enter the address")
                Field(manual, { manual = it.trim() }, "PC address", hint = "Shown in the companion's window, e.g. 192.168.1.20", mono = true)
                Gap(8)
                SlantButton("Connect", { onConnect(manual) }, style = SlantStyle.OUTLINE, enabled = manual.isNotBlank() && connection.status != ConnectionStatus.CONNECTING)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Capabilities(c: Capabilities) {
    val items = listOf(
        "Keyboard" to c.keyboard, "vJoy gamepad" to c.vjoy, "Xbox pad" to c.vigem,
        "BeamNG mod" to c.beamng, "OutGauge telemetry" to c.outgauge,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (name, on) ->
            Row(
                Modifier
                    .height(32.dp)
                    .clip(SlantShape())
                    .background(if (on) Race.Cap else Race.Ground)
                    .border(1.dp, if (on) Race.Green.copy(alpha = 0.6f) else Race.Line, SlantShape())
                    .padding(horizontal = 14.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$name ${if (on) "available" else "not available"}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLamp(if (on) Race.Green else Race.InkFaint, lit = on, size = 7)
                Spacer(Modifier.width(6.dp))
                Text(name.uppercase(), style = RaceType.unit, color = if (on) Race.Ink else Race.InkFaint)
            }
        }
    }
}
