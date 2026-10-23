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

Every beta is on the GitHub releases page: https://github.com/alialyami90/GameControl/releases. Each one has
`GameCtl.apk` (phone app), `GameCtl-companion-win-x64.zip` (PC companion), `gamecontroller.zip` (BeamNG mod),
this guide and `SMOKE_TEST.md`, the single end-to-end test to run after installing.

Install the optional drivers (vJoy, ViGEmBus, section 2b) **before** you start the companion, or restart the companion after
installing them, so it finds them.

## 1. Install the app

1. Open `GameCtl.apk` on the phone. Allow "Install unknown apps" for your browser or file manager when asked. The beta is
   signed with a test key, so Play Protect may warn: choose **More details > Install anyway**.
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
4. Check it in Windows: press `Win+R`, run `joy.cpl`, select **GameCtl Pad**, and press buttons on the phone.

Some phone makers remove Bluetooth HID support. Then the Bluetooth panel is a single grey line saying so, and the PC companion
panel comes first: use the companion.

## 2b. Connect over Wi-Fi with the PC companion

1. Unzip `GameCtl-companion-win-x64.zip` and run `GameControllerCompanion.exe`. If SmartScreen says it protected your PC,
   choose **More info > Run anyway**. Allow it through the Windows firewall on **Private networks** when asked (your Wi-Fi
   must be set to Private in Windows). The window "GAME CONTROLLER Companion for Windows" opens, a tray icon appears, and it
   shows a 6-digit PIN.
2. Optional drivers:
   - **vJoy** (extra joystick buttons and axes through the companion): install the maintained fork from
     https://github.com/BrunnerInnovation/vJoy/releases and enable device 1 with 128 buttons and all axes in *Configure vJoy*.
   - **ViGEmBus** (virtual Xbox pad for XInput-only games): https://github.com/nefarius/ViGEmBus/releases. The project is
     archived upstream, so it is optional.
3. On the phone open **Connect > PC companion**. Your PC appears in the list (same Wi-Fi network). Tap it, enter the PIN.
   If it doesn't appear, type the PC's IP address (shown in the companion window).

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
- Make your own: **New profile**, then **Edit**. Add controls from the palette, drag to move, drag the corner to
  resize, and set what each one does in the inspector: keys, gamepad buttons/axes, macros, BeamNG commands or Xbox
  buttons. Profiles export and import as `.gcprofile.json` files.

## Building from source

- Android: JDK 17 and the Android SDK (API 35). `cd android && ./gradlew :core:test :app:assembleRelease`
  writes `app/build/outputs/apk/release/app-release.apk`.
- Windows companion: .NET 8 SDK. See `windows/README.md`.
- BeamNG mod: `sh beamng-mod/build.sh` writes `beamng-mod/dist/gamecontroller.zip`. Tests: `cd beamng-mod && luajit tests/run.lua`.
- GitHub Actions (`.github/workflows/ci.yml`) builds and tests all three on every push and uploads the APK,
  the companion and the mod zip as artifacts.
