# RC2 replies: Windows companion

This is synced to `windows/`. `dotnet test`: 133/133 pass, 0 warnings, including 8 new tests. The publish is still one `GameCtlCompanion.exe`; its PE version resource reads FileVersion 1.0.0.0 and ProductVersion 1.0.0.

- **M3, a transient lock wipes pairings:** fixed (CompanionConfig.cs `Load`).
  - A read that fails with IOException or UnauthorizedAccessException is retried for about 3 s (6 attempts).
  - If the file stays locked, the store marks itself `WriteBlocked`, and every Save or Update then throws without touching the file. This session runs on defaults, which can't replace the real file.
  - The file is set aside only for a real parse error (JsonException). If that copy fails, it is write-blocked too.
  - The Status page shows the reason in its banner, and the log has a warning that says the pairings are not lost and asks for a restart.
  - Tests: `A_briefly_locked_file_is_read_after_a_retry`, `A_file_that_stays_locked_is_never_overwritten` (the file is byte-identical afterwards, and a pairing attempt returns SaveFailed), and `An_unreadable_file_that_cannot_be_set_aside_is_left_alone`.
- **W-1, Bluetooth scan errors swallowed and a late event after Dispose:** fixed.
  - `ScanBluetooth` now lets a Present() failure through to the loop. The loop logs "Bluetooth scan failed: ..." once per distinct error.
  - After the stop is requested, a scan in flight raises no event and logs nothing.
  - `_stop` is disposed, either straight away or once a scan stuck in SetupAPI finishes.
  - `Dispose()` is now idempotent.
  - Test: `Bluetooth_scan_failures_are_logged_once_and_the_pin_is_not_logged`.
- **W-2, the log grows past the cap when rotation fails:** fixed (FileLog `Rotate`). If `.1` can't be replaced, the current file is truncated and restarts with a "Log restarted" line. If it can't even be truncated, writing stops. Test: `Stays_capped_when_the_old_file_cannot_be_replaced`, which makes `.1` a non-empty folder.
- **W-3, the second instance shares the log:** fixed.
  - A copy started with `--allow-second` writes `%APPDATA%\GameController\companion-second.log`.
  - It also no longer creates the show-window event, which belongs to the first copy, and no longer repairs the Run key.
- **W-4, a newer settings file is downgraded:** fixed. A file with a `schema` above 1 is loaded for what we understand, so its phones still sign in. It is never migrated or saved, and a warning is logged. Test: `A_newer_settings_file_is_used_but_never_rewritten`, which keeps the unknown fields and schema 2.
- **W-5, the PIN is in the log:** fixed.
  - The start-up line now reads "The pairing PIN is shown in the window (never written to the log)". No other log line contains the PIN or a token. The test above asserts this.
  - The console host prints the PIN to its own console only.
- **W-6, versions set separately:** fixed. Directory.Build.props sets only `VersionPrefix` 1.0.0, and the SDK derives Version, AssemblyVersion, FileVersion and InformationalVersion from it. `-p:Version=...` overrides all of them. The UI (Settings footer and tray) now reads InformationalVersion.
- **W-7, the OS window title:** fixed. The title is "GameCtl Companion", for the taskbar and Alt-Tab. The in-window rail header keeps "GAMECTL".

Path addition for INSTALL: `%APPDATA%\GameController\companion-second.log` exists only when someone runs a second copy with `--allow-second`, a developer option.

## RC3

This is synced to `windows/`. `dotnet test`: 134/134 pass, 0 warnings. The win-x64 publish is clean.

- **M4, a repeated key crashes start-up:** fixed (CompanionConfig.cs `Load`).
  - The read step and the parse step are separate. Only IO errors are retried, and they are retried only in the read step.
  - The parse step now catches every exception: the file is already in memory there, so any failure means its content is bad. That covers bad JSON, a repeated key, or a wrong type. The file is set aside and defaults are used, as for any unreadable file.
  - Test: `A_repeated_key_is_treated_as_unreadable_not_a_crash`. It uses `"serverName"` twice, and checks that there is no throw, that the note is set, and that the set-aside copy is byte-identical.
- **W-8, the retry length:** my RC2 reply was wrong.
  - The retry is 6 reads with 1.5 s of waits in all (100 + 200 + 300 + 400 + 500 ms), not about 3 s.
  - I kept 1.5 s on purpose. It runs once at start-up, before the window is shown, and a lock that outlasts it leaves the file untouched (WriteBlocked) rather than losing anything.
  - The comment at the retry now says this.
- **W-9, the Settings note when saving is blocked:** fixed. When the store is write-blocked, the note reads "Not saved: " followed by the same text as the banner (close whatever holds the file, then restart). "Try again in a moment" now appears only for a normal failed write.
- **W-10, the second instance's log path:** fixed. Settings shows `companion-second.log` when the copy runs with `--allow-second`.
- **W-11, a misplaced doc comment:** fixed. `_lastScanError` now sits above the comment, which documents `ScanBluetoothLoop` again.
