# Round 5 review (dev 274ebbc, CI run 39882596398 green: core 26, Windows 121, mod 150)

**Verdict: nothing blocks. Publish beta-2.** ALI's beta-1 issue is fixed in code: the companion now lists a phone connected over Bluetooth. Real Windows HID enumeration can't run here, so ALI's beta-2 test is the proof.

## Verified
- **Bluetooth phone shows in the companion.** WindowsBluetoothPads lists the present "GameCtl Pad" HID interfaces and collapses the keyboard and gamepad collections into one pad (BluetoothPadKey.FromHidPath). CompanionHost scans every 2 s and logs connect and disconnect. The window, status line and tray all use Host.Phones(), so a phone with both links counts once. The UI thread is marshalled correctly.
- **Phone side.** When Bluetooth connects, the phone reconnects the Wi-Fi link to the last paired PC, so lamps and BeamNG commands keep working.
- **A-S1** rehomeLatches with test as1. **A-S2** the editor snackbar is gone and the row shows result.message. **W-S1**, **U20** (New profile is now the first list row), **U35**, **U36**, and the cap labels are all fixed. **B-S2** and **B-S3** are fixed. **B-S1** is accepted as a known limit.
- **A-S3** was my own probe. I've retired it because A28 removed the latch park on purpose.
- **OutGauge diagnostics** log once per reason and show port-in-use and the last packet.

## SHOULD, next cycle
- **R5-1. Bluetooth scan runs on the engine tick.** ScanBluetooth (CompanionHost.cs Tick, every 20th tick) walks SetupAPI and opens every HID device on the 100 ms tick thread. On a PC with many HID devices, or a slow Bluetooth device, that stalls auto-release and resend for that tick. Run the scan on its own task with a guard, or use a device-change notification.
- **R5-2. Bluetooth overrides an explicit Disconnect.** AppViewModel calls companion.connect(host) whenever HID connects, and connect() clears userDisconnected. A user who tapped Disconnect gets reconnected the next time Bluetooth reconnects. Only reconnect when the user didn't disconnect on purpose.
- **R5-3. Pairing by order with several phones.** PhoneLinks.Merge pairs sessions and pads by order, so with two phones the BT tag can sit on the wrong one. This is documented and fine for one phone. Note it in INSTALL troubleshooting.
