# Round 4 replies: Windows companion

Synced to `windows/`. `dotnet test`: 119/119 pass, 0 warnings. `dotnet publish -r win-x64` is clean. The screenshots in `windows/docs/screenshots/` are refreshed.

- **Beta-1 Bluetooth shows "disconnected":** fixed.
  - Bluetooth HID input goes straight to Windows, so the companion now looks for it. `CompanionHost.ScanBluetooth()` runs at start and then every 2 s, through `IBluetoothPads`.
  - On Windows, `WindowsBluetoothPads` asks SetupAPI for present HID interfaces (`DIGCF_PRESENT`) whose product string is "GameCtl Pad". Collections are collapsed to one pad per device with `BluetoothPadKey.FromHidPath`.
  - Connects and disconnects are written to the Log, and the change raises `BluetoothChanged`.
- **Shown in the window and tray, separate from Wi-Fi:** done.
  - On the signal path, the phone node has its own WI-FI and BT tags. A tag is green-filled when that link is up and hollow when it is down.
  - The wire to the PC is solid only when the WI-FI tag is up.
  - A Bluetooth-only node uses the Bluetooth icon, a "Ready" state and the detail "Bluetooth · Wi-Fi not linked".
  - The Phones page status reads "Wi-Fi + Bluetooth", "Wi-Fi", "Bluetooth only" or "Offline".
  - The rail reads "1 phone · Bluetooth only".
  - The tray tooltip says the phone is on Bluetooth only and that Wi-Fi is not linked, with the PIN.
- **Both links up, phone counted once:** done.
  - `PhoneLinks.Merge` pairs pads with Wi-Fi sessions in order and returns one `LinkedPhone` per phone. All counts use it: rail, tray and the Phones badge.
  - Pads are matched by order because every phone presents the same name and Android exposes no Bluetooth address. With two phones on both links, the tags could sit on the wrong row, but the counts stay right.
- **"Wi-Fi: not linked" kept visible, with a hint:** done.
  - On Status, an amber banner explains that controls reach Windows directly, and that lamps, game state and BeamNG commands need the Wi-Fi link. It tells the user how to link: tap Connect, pick this PC, type the PIN.
  - The banner shows "WI-FI / Not linked" on its right. The PIN card stays in its "needs attention" state until Wi-Fi links. See shots 12 and 13.
- **Paired pad switched off drops out:** done in code, but not verified on hardware.
  - SetupAPI only returns a device with `DIGCF_PRESENT` while its HID interfaces exist. Windows removes those when a classic Bluetooth HID link drops, so the pad leaves on the next 2 s scan.
  - A pairing that Windows remembers stays under Bluetooth settings, but it is not a present HID device.
  - ALI should confirm this on a real PC by turning off the phone's Bluetooth with the window open.
- **Product string matches:** this is assumed, and I could not verify it here.
  - The filter expects the HID product string to equal the SDP name `HidDescriptor.NAME` ("GameCtl Pad").
  - If Windows reports a different string on some stack, the pad will not show. The Log will then say nothing about Bluetooth, which is a quick way to spot it.
- **Test with a fake identity source:** added.
  - `HostIntegrationTests.Bluetooth_pad_is_shown_and_counted_once_with_its_wifi_link` uses `FakePads`. It checks a Bluetooth-only phone, then the same phone adding Wi-Fi and being counted once, then the pad switching off and the Wi-Fi link remaining.
  - `BluetoothPadTests` covers the two HID-path key cases: collections, and the instance suffix.
- **W-S1, clear Rebound in Tick:** fixed.
  - `BindingEngine.Tick` now clears `Rebound` whenever it resets `LastApplied` for a silent momentary control.
  - The test `Rebind_then_lost_release_does_not_swallow_the_next_press` covers it.
- **UI polish found while checking the shots:**
  - Link tags now sit on the node's second line, so the phone name has the full first line and no longer collides with the tags.
  - The italic "Not linked" in the banner no longer clips its last letter.

## Beta-2: OutGauge diagnostics

Synced. `dotnet test`: 121/121 pass.

1. **Port in use:** when the OutGauge listener fails to start, the Telemetry row turns Attention and reads "Port N is in use by another program (for example SimHub)". N is the configured port, 4444 by default. The node on the signal path uses the same state. Test: `OutGauge_port_in_use_is_reported_as_failed`.
2. **Ignored packets:** `OnOutGauge` logs each of these once per run, as a warning:
   - "OutGauge packet from <addr> ignored (not this PC)"
   - "OutGauge packet too short (<n> bytes)"

   Test: `OutGauge_ignored_packets_are_logged_once_per_reason`.
3. **Setup text:** the row shows the new text, with the configured port, while OutGauge is on and no packets arrive.
   - While the mod is linked, the row shows only "While the mod is connected, the mod's data is used instead.", whether or not packets arrive.
   - This hint is now always visible. Before, a Ready row hid its setup box, so the old OutGauge hint never showed.
4. **Last packet line:** the detail reads "Last packet: Ns ago, from <addr>" and refreshes every 2 s.
   - It counts any packet, including ignored ones, so a packet from another PC shows up too.
   - Before the first packet it reads "No packet yet".
