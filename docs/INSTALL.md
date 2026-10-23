# Installing and using GameCtl

GameCtl turns an Android phone or tablet (Android 9 or newer) into an extra controller for Windows games.
There are two ways to connect, and you can use both at the same time.

| | Bluetooth controller | PC companion (Wi-Fi) |
|---|---|---|
| PC software needed | None | GameCtl Companion (tray app) |
| What Windows sees | A Bluetooth keyboard + gamepad called **GameCtl Pad** (64 buttons, 8 axes) | Keyboard scancodes, vJoy joystick, optional virtual Xbox pad |
| Works with | Any game that reads a keyboard or a DirectInput / HID gamepad | Any game, including XInput-only games (with ViGEm) |
| Lamps on the phone follow the game | No (lamps follow your own taps) | Yes, with the BeamNG mod or OutGauge |
| BeamNG game commands and macros | Macros yes, BeamNG commands no | Yes |

## 0. What you need and where to get it

- An Android phone or tablet, Android 9 or newer.
- A Windows 10 or 11 PC, 64-bit. .NET is not needed: the companion is a self-contained exe.

Download the latest release from https://github.com/alialyami90/GameControl/releases. It has
`GameCtl.apk` (phone app), `GameCtl-companion-win-x64.zip` (PC companion), `gamecontroller.zip` (BeamNG mod),
this guide and `SMOKE_TEST.md`, the single end-to-end test to run after installing.

Install the optional drivers (vJoy, ViGEmBus, section 2b) **before** you start the companion, or restart the companion after
installing them, so it finds them.

## 1. Install the app

> **Upgrading from an earlier build?** The betas and 1.0 are signed with test keys that differ between builds, so
> Android won't install 1.0 over a beta ("App not installed"), and the same applies to the next version. Do this each time:
> 1. In the beta, open each profile you made or changed, tap its card menu and **Export to file**.
> 2. Uninstall the beta (this deletes its profiles and paired PCs).
> 3. Install 1.0 as below, then **Import** each file with the download icon at the top right of the profile list.
> 4. Connect to the PC companion again: it asks for the PIN once. The companion keeps its own list of phones.
>
> Later versions install over 1.0 directly.

1. Open `GameCtl.apk` on the phone. Allow "Install unknown apps" for your browser or file manager when asked. Because the
   app comes from outside the Play Store, Play Protect may ask to scan it or warn once: choose **Install anyway**.
2. Open **GameCtl**. The app runs in landscape.

## 2a. Connect over Bluetooth (no PC software)

1. In GameCtl tap the **PAIRED / OFF** badges at the top right of the profile list to open Connect, then in the Bluetooth
   panel tap **MAKE DISCOVERABLE**. Allow the "Nearby devices" permission and allow the phone to be visible.
2. On the PC: **Settings > Bluetooth & devices > Add device > Bluetooth**, pick **GameCtl Pad**. If Windows shows a code,
   confirm it on the phone too. If **GameCtl Pad** never shows up, discoverable mode has ended (it lasts about 2 minutes):
   tap **MAKE DISCOVERABLE** again and search again.
   **Game sound gone after pairing?** Windows also adds the phone's own audio (headset / hands-free) when it pairs and can
   switch the PC's sound output to the phone. Open **Control Panel > Devices and Printers**, right-click the phone,
   **Properties > Services**, untick the audio, headset and hands-free services, and set your speakers or headphones back as
   the default output (sound icon in the taskbar). Restart the game if it still has no sound.
3. Next time, open GameCtl and pick your PC under **Paired PCs**; it reconnects.
   With several phones on one PC, the companion matches each Bluetooth pad to a Wi-Fi phone in the order they connected,
   so connect one phone at a time if the Phones list should show which pad is which.
4. Check it in Windows: press `Win+R`, run `joy.cpl`, select **GameCtl Pad**, and press buttons on the phone.

Some phone makers remove Bluetooth HID support. Then the Bluetooth panel is a single grey line saying so, and the PC companion
panel comes first: use the companion.

## 2b. Connect over Wi-Fi with the PC companion

1. Unzip `GameCtl-companion-win-x64.zip` and run `GameCtlCompanion.exe`. If SmartScreen says it protected your PC,
   choose **More info > Run anyway**. Allow it through the Windows firewall on **Private networks** when asked (your Wi-Fi
   must be set to Private in Windows). The window "GameCtl Companion" opens, a tray icon appears, and it
   shows a 6-digit PIN.
2. Optional drivers:
   - **vJoy** (extra joystick buttons and axes through the companion): install the maintained fork from
     https://github.com/BrunnerInnovation/vJoy/releases and enable device 1 with 128 buttons and all axes in *Configure vJoy*.
   - **ViGEmBus** (virtual Xbox pad for XInput-only games): https://github.com/nefarius/ViGEmBus/releases. The project is
     archived upstream, so it is optional.
3. On the phone open **Connect > PC companion**. Your PC appears in the list (same Wi-Fi network). Tap it, enter the PIN.
   If it doesn't appear, type the PC's IP address (shown in the companion window).
4. Closing the window keeps the companion running in the tray; use **QUIT COMPANION** on the window's side rail (or the tray
   menu) to stop it. To start it with Windows, turn on **Settings > Startup > Start with Windows** in the companion.

## 3. BeamNG.drive

1. Copy `gamecontroller.zip` (still zipped) into `%LOCALAPPDATA%\BeamNG.drive\<version>\mods\`. In BeamNG,
   **Options > Other > Open user folder** opens that folder; the zip goes in its `mods` folder.
2. Start BeamNG with the companion running. In the companion window the node **BeamNG mod** shows "Mod 1.0.0 connected".
3. On the phone open the **BeamNG.drive** profile: every vehicle function, lamps light from the real game state,
   and controls the current vehicle doesn't have (fog lights, diff locks, low range...) are dimmed.
4. Prefer Bluetooth? Use **BeamNG.drive (Bluetooth pad)**. Bind it once: with the companion connected, BeamNG running with the
   mod, and the pad known to the companion (phone paired over Bluetooth, or vJoy installed), open the profile, tap **Edit**,
   then on a phone the **gear icon** in the top bar (on a tablet the right-hand panel), scroll to **BEAMNG** and tap
   **Bind in game**. BeamNG maps every button to its function. You can also bind them by hand in BeamNG's *Options > Controls*.
5. No mod? BeamNG's **OutGauge** gives basic lamps (high beam, signals, handbrake, TC, ABS, gear, speed) through the
   companion. In BeamNG open **Options > Other**, tick **OutGauge**, set IP **127.0.0.1** and port **4444**, then drive a car
   (press **Ctrl+R** once if it was already loaded). It only sends while you are in a vehicle, not in menus. While the mod is
   connected, the mod's data is used instead. If the companion's Telemetry row says port 4444 is in use, close the other program
   using it (for example SimHub).

## 4. Other games

- **Generic gamepad** and **Keyboard keys** work in any game. Bind the buttons in the game's own controls menu.
- **MudRunner** uses the game's default keys (Q diff lock, E AWD, R gear, F winch, T handbrake, H lights, G horn).
  Map and camera are set to M and C; edit them if your bindings differ.
- **Bulldozer** and **Utility vehicles** (loader, excavator, crane, plough & tow) are machine cabs for any game with heavy
  equipment: pilot joysticks (two spring-centred axes each), hydraulic levers and switchgear. Every control is a gamepad axis or
  button: open the game's controls menu once and bind each function by moving the matching stick or pressing the button.
  Common jobs keep the same button on every machine (1 start, 2 horn, 3 beacon, 4 lights, 6 park brake, 11 camera).
  Jobs only one machine has can share a button with another machine's own job, so bind each machine on its own.
  Every machine button is 32 or below, so games that only read 32 joystick buttons see them all.
- Make your own: **New profile**, then **Edit**. Add controls from the palette, drag to move, drag the corner to
  resize, and set what each one does in the inspector: keys, gamepad buttons/axes, macros, BeamNG commands or Xbox
  buttons. Profiles export and import as `.gcprofile.json` files.

## Troubleshooting

- **The phone doesn't find the PC.** Both must be on the same Wi-Fi, and that network must be set to **Private** in Windows.
  Guest networks and some routers isolate devices from each other ("AP isolation"); a VPN on the PC or the phone, or the
  phone on mobile data, has the same effect. Type the PC's IP address from the companion window if the list stays empty.
- **The PIN is rejected.** Type the PIN currently shown in the companion window (it changes after each pairing). After 5 wrong
  tries the companion waits a minute.
- **Bluetooth pairs but nothing moves in joy.cpl.** On the PC remove **GameCtl Pad** in Settings > Bluetooth & devices, then on
  the phone tap **MAKE DISCOVERABLE** and add it again.
- **The PC's sound moved to the phone after pairing.** See section 2a, step 2.
- **No lamps in BeamNG.** Check that **GameCtl for BeamNG** is enabled in BeamNG's mod manager and that the companion window shows
  "Mod 1.0.0 connected".
- **Reporting a problem.** The companion's settings and log are in `%APPDATA%\GameController\` (`companion.json`,
  `companion.log`); **Settings > Settings and log > OPEN FOLDER** in the companion opens it. Attach `companion.log` (a second copy started with `--allow-second` writes `companion-second.log`). The pairing PIN is masked in the log.

## Uninstalling

- **Phone:** uninstall GameCtl like any app (this deletes its profiles; export the ones you want to keep first).
- **PC:** in the companion turn off **Start with Windows**, quit it, then delete its folder and `%APPDATA%\GameController\`.
  Optionally remove its firewall rule: `netsh advfirewall firewall delete rule name="GameCtl Companion"`.
- **Bluetooth:** remove **GameCtl Pad** in Windows Settings > Bluetooth & devices.
- **BeamNG:** delete `gamecontroller.zip` from the `mods` folder and the file ending in `_gamecontroller.json` from
  `settings\inputmaps\` in the BeamNG user folder.

## Building from source

- Android: JDK 17 and the Android SDK (API 35). `cd android && ./gradlew :core:test :app:assembleRelease`
  writes `app/build/outputs/apk/release/app-release.apk`. Release signing comes only from the environment, never from
  the repository: set `GAMECTL_KEYSTORE` (path to the `.jks`), `GAMECTL_KEYSTORE_PASSWORD` and `GAMECTL_KEY_ALIAS`
  (default `gamectl`). The key password is taken to be the store password; set `GAMECTL_KEY_PASSWORD` if yours differs.
  Without them the release APK is signed with your local debug key, which is fine for testing but won't install over the
  official app. `./gradlew -PcoreOnly=true :core:test` tests the core without the Android SDK.
- Windows companion: .NET 8 SDK. See `windows/README.md`.
- BeamNG mod: `sh beamng-mod/build.sh` writes `beamng-mod/dist/gamecontroller.zip`. Tests: `cd beamng-mod && luajit tests/run.lua`.
- GitHub Actions (`.github/workflows/ci.yml`) builds and tests all three on every push and uploads the APK,
  the companion and the mod zip as artifacts. The release key lives in the repository secrets `ANDROID_KEYSTORE_B64`,
  `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_ALIAS`; CI decodes it to a temporary file for the build. Running the
  workflow by hand with `release_tag` (for example `v1.0.0`) publishes a GitHub release; a `v*` release is refused unless
  the APK is signed with the GameCtl release key (its SHA-256 certificate fingerprint is pinned in the workflow), unless
  `allow_test_key` is ticked. 1.0.0 was released that way, with a test key.
