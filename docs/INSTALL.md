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

## 0. Download

Every beta is on the GitHub releases page: https://github.com/alialyami90/GameControl/releases. Each one has
`GameCtl.apk` (phone app), `GameCtl-companion-win-x64.zip` (PC companion), `gamecontroller.zip` (BeamNG mod),
this guide and `SMOKE_TEST.md`, the single end-to-end test to run after installing.

## 1. Install the app

1. Copy `GameCtl.apk` to the phone and open it. Allow "Install unknown apps" for your file manager when asked.
2. Open **GameCtl**. The app runs in landscape.

## 2a. Connect over Bluetooth (no PC software)

1. In GameCtl tap the connection chip (top right of the profile list), then **Bluetooth controller > Enable**.
   Allow the "Nearby devices" permission and allow the phone to be visible.
2. On the PC: **Settings > Bluetooth & devices > Add device > Bluetooth**, pick **GameCtl Pad**.
3. Next time, open GameCtl and pick your PC under **Paired PCs**; it reconnects.
4. Check it in Windows: press `Win+R`, run `joy.cpl`, select **GameCtl Pad**, and press buttons on the phone.

Some phone makers remove Bluetooth HID support. If **Enable** says the phone does not support it, use the companion.

## 2b. Connect over Wi-Fi with the PC companion

1. On the PC run `GameCtl.Companion.exe` (from `GameCtl-companion-win-x64`). Allow it through the Windows firewall
   on private networks when asked. It sits in the tray and shows a 6-digit PIN.
2. Optional drivers:
   - **vJoy** (extra joystick buttons and axes through the companion): install the maintained fork from
     https://github.com/BrunnerInnovation/vJoy/releases and enable device 1 with 128 buttons and all axes in *Configure vJoy*.
   - **ViGEmBus** (virtual Xbox pad for XInput-only games): https://github.com/nefarius/ViGEmBus/releases. The project is
     archived upstream, so it is optional.
3. On the phone open **Connect > PC companion**. Your PC appears in the list (same Wi-Fi network). Tap it, enter the PIN.
   If it doesn't appear, type the PC's IP address (shown in the companion window).

## 3. BeamNG.drive

1. Copy `gamecontroller.zip` into `Documents\BeamNG.drive\<version>\mods\` (or BeamNG's user folder `mods\`).
2. Start BeamNG with the companion running. The companion window shows **BeamNG: connected**.
3. On the phone open the **BeamNG.drive** profile: every vehicle function, lamps light from the real game state,
   and controls the current vehicle doesn't have (fog lights, diff locks, low range...) are dimmed.
4. Prefer Bluetooth? Use **BeamNG.drive (Bluetooth pad)**: open it, tap **Edit > Bind in game** once (companion + mod
   running), and BeamNG maps every button to its function. You can also bind them by hand in BeamNG's
   *Options > Controls*.
5. No mod? BeamNG's **OutGauge** (Options > Other > OutGauge, IP 127.0.0.1, port 4444) gives basic lamps
   (high beam, signals, handbrake, TC, ABS, gear, speed) through the companion.

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
