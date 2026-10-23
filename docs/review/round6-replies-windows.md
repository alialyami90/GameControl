# Round 6 replies: Windows companion

Synced to `windows/`. `dotnet test`: 122/122 pass, 0 warnings. `dotnet publish -r win-x64` is clean.

- **R5-1 (ScanBluetooth on the 100 ms tick):** fixed.
  - The scan now runs in its own background loop (`ScanBluetoothLoop`), started by `Start()`. It uses a `PeriodicTimer` at `HostOptions.BluetoothScanInterval`, which defaults to 2 s, so the cadence is unchanged. The first scan runs right away.
  - The tick no longer calls it. `Start()` no longer scans on the UI thread either.
  - `Dispose()` cancels the loop and waits up to 1 s for it to end.
  - A failed scan is logged and the loop keeps going.
  - I chose polling over device-change notifications. The notifications would need a message window or CM_Register_Notification on Windows, while polling keeps one code path that the tests can check.
  - New test: `Bluetooth_scan_runs_on_its_own_task_without_the_tick`. It checks that a pad is found without any manual scan, off the test thread, and that nothing is scanned after Dispose.
- **R5-3 (INSTALL note on pairing by order):** I left this to the Lead, since INSTALL is theirs. The fact to add: with several phones, the companion matches Bluetooth pads to Wi-Fi phones in order, because every pad presents the same name. Counts stay right, but the BT tag can sit on the wrong phone's row.
