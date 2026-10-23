# RC1 replies: Windows companion (1.0)

This is synced to `windows/`. `dotnet test`: 127/127 pass, 0 warnings. `dotnet publish src/Companion.App -c Release -r win-x64` produces one file, `GameCtlCompanion.exe`, with symbols embedded. The screenshots in `windows/docs/screenshots/` are refreshed.

## First list

- **Version 1.0.0 everywhere:** done.
  - A new `windows/Directory.Build.props` sets the same version for every assembly:
    - Version 1.0.0
    - AssemblyVersion and FileVersion 1.0.0.0
    - InformationalVersion 1.0.0
  - I checked the published exe's version resource: FileVersion 1.0.0.0, ProductVersion 1.0.0.
  - The Settings footer reads "GameCtl Companion 1.0.0 · protocol v1".
  - The tray tooltip starts with "GameCtl Companion 1.0.0:" and is capped at Windows' 127-character limit.
- **App icon in the exe:** done. `ApplicationIcon` is `Assets/companion.ico`, which has 7 sizes from 16 to 256. I checked the published PE: it has RT_GROUP_ICON plus 7 RT_ICON entries. The window and the tray use the same artwork.
- **First run without vJoy, ViGEm or BeamNG reads as optional:** done.
  - When vJoy, ViGEmBus or the BeamNG mod is not installed, its row reads "Optional" with a hollow lamp, not "Needs setup". vJoy and Xbox show "Not installed (optional)" and a one-line reason.
  - Each setup box says what the component is for, then how to install it.
  - The Outputs badge stays empty, and nothing is logged as a warning.
  - "Needs setup" (amber) is now kept for a driver that is installed but unusable: vJoy disabled, no free device, a wrong DLL version, or ViGEmBus failing.
  - See shots 01 and 03.
- **Close keeps it in the tray, with a clear Quit:** done.
  - Closing the window has always hidden it to the tray. Windows sign-out and shutdown really quit.
  - New: a "QUIT COMPANION" button at the bottom of the side rail, with "Closing the window keeps it in the tray" under it and a tooltip.
  - The tray menu items are now "Open GameCtl Companion" and "Quit GameCtl Companion".
  - Before this, the window had no Quit at all, only the tray had one.
- **Beta-2 config and paired phones survive the upgrade:** done, with tests.
  - The settings file keeps its folder and name: `%APPDATA%\GameController\companion.json`. Its layout has not changed since beta-1.
  - New: a `schema` number (1). A file without it (any beta) is upgraded on load. `Migrate()` fills missing values, puts out-of-range ports back to their defaults, drops pairings with no id or token, and keeps the newest of any duplicates. The file is then saved and the upgrade is logged.
  - Reading is now tolerant of case, quoted numbers, comments and trailing commas.
  - An unreadable file used to be overwritten silently with defaults, which lost every pairing. Now it is kept as `companion.json.unreadable-<date>`, defaults are used, and a warning is logged.
  - Tests in `ConfigMigrationTests`:
    - `Beta2_settings_and_paired_phones_survive_the_upgrade`: an exact beta-2 file keeps all its settings, the phone signs in with its old token, and the schema is written.
    - `Unreadable_file_is_kept_aside_not_overwritten_silently`.
    - `Bad_values_are_repaired_and_unusable_pairings_dropped`.
- **Unhandled exception paths:** done.
  - New log file next to the settings file, `companion.log`. Each file stays under 1 MB, with one older file kept. Every log line goes to it.
  - The handlers now in place:
    - Window (dispatcher) exceptions are logged with their stack and marked handled.
    - Process-level exceptions are logged, then input is released.
    - Unobserved task faults are logged and marked observed. Before, they were swallowed with no record.
  - `CompanionHost.Log` calls each listener inside its own try, so a failing listener cannot break a network loop.
  - Other paths now caught and logged: the macro task, the release of a held BeamNG command, adding the firewall rule, and the "open folder" and "open link" actions.
  - The network loops, the tick and the Bluetooth scan already had top-level catches. I re-checked them.

## Second list

1. **One product name:** done.
   - AssemblyName is `GameCtlCompanion`, so the exe is `GameCtlCompanion.exe`.
   - Window title: "GAMECTL Companion". Rail header: "GAMECTL".
   - Product and FileDescription: "GameCtl Companion". Company: "GameCtl".
   - The tray tooltip and menu, the console banner and README are updated.
   - BeamNG messages now name the mod "GameCtl for BeamNG", matching its info.json.
   - The settings and log folder stays `%APPDATA%\GameController\`, so beta configs carry over with no move.
   - The single-instance mutex keeps its beta name, so a beta and 1.0 can't run at the same time.
   - The firewall rule is now "GameCtl Companion". "Allow" deletes the beta rule "Game Controller Companion" first. The beta rule pointed at the old exe and no longer matched anyway.
   - CI finds `Companion.App.csproj`, so it is unaffected. The artifact folder now holds `GameCtlCompanion.exe`.
   - These files outside `windows/` still say `GameControllerCompanion.exe`, for you to update: `docs/INSTALL.md`, `docs/SMOKE_TEST.md`, `review/rc1-docs.md`.
2. **Start with Windows toggle:** this already existed in Settings > Startup, writes the HKCU Run key, and is off by default.
   - The value is now `HKCU\Software\Microsoft\Windows\CurrentVersion\Run\GameCtlCompanion` = `"<exe path>" --minimized`.
   - At start-up, the beta entry `GameControllerCompanion` is removed. If it existed, or the saved setting is on, the new entry is written for the current exe. That also fixes a stale path after the exe is moved.
3. **Exact paths:**
   - Settings: `%APPDATA%\GameController\companion.json` (usually `C:\Users\<name>\AppData\Roaming\GameController\companion.json`).
   - Log: `%APPDATA%\GameController\companion.log`, plus `companion.log.1` (the older file). Each is kept under 1 MB.
   - Unreadable settings set aside: `%APPDATA%\GameController\companion.json.unreadable-<yyyyMMdd-HHmmss>`.
   - Settings > "Settings and log" shows both paths, and OPEN FOLDER opens that folder.
   - Uninstall:
     - Quit from the tray or the rail.
     - Delete the exe.
     - Delete `%APPDATA%\GameController\`.
     - Turn off Start with Windows first, or delete the Run value `GameCtlCompanion`.
     - Optionally remove the firewall rule "GameCtl Companion", or "Game Controller Companion" from a beta: `netsh advfirewall firewall delete rule name="GameCtl Companion"`.
4. **Final Bluetooth-only strings for SMOKE A2a:**
   - Status page banner, right side: a small caps label "WI-FI" over "Not linked" in amber italic.
   - Banner text: "A phone is connected over Bluetooth, so its controls reach Windows directly. Lamps, game state and BeamNG commands need the Wi-Fi link too: in the app tap Connect, pick this PC and type the PIN."
   - Signal-path phone node:
     - Tags "WI-FI" (hollow) and "BT" (green filled).
     - Title "GameCtl Pad".
     - Detail "Bluetooth · Wi-Fi not linked".
   - With both links up, both tags are green filled.
   - Phones page status: "Bluetooth only". Other values: "Wi-Fi + Bluetooth", "Wi-Fi", "Offline".
   - Rail: "1 phone · Bluetooth only".
   - Tray tooltip: "GameCtl Companion 1.0.0: 1 phone on Bluetooth only (Wi-Fi not linked, PIN 123456)".

## Not verified here (Linux build machine)

These need a real Windows PC:
- the Run key repair;
- the firewall rule rename;
- the icon as it shows in Explorer and the taskbar.

The code paths are small and guarded, and the PE resources are checked.
