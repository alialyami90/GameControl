# GameCtl developer handover

GameCtl turns an Android phone or tablet into an extra control surface for Windows games. This page is the starting point for a developer taking the project over. For users, read [INSTALL.md](INSTALL.md). For the 1.0 feature list, read [RELEASE_NOTES.md](RELEASE_NOTES.md).

## The three parts

| Part | Folder | Stack | Tests |
|---|---|---|---|
| Android app | `android/` | Kotlin 2.0, Jetpack Compose, AGP 8.7, Gradle 8.10, minSdk 28, JDK 17 | `./gradlew :core:test` (JUnit, pure JVM) |
| Windows companion | `windows/` | C# / .NET 8, Avalonia UI, self-contained single exe | `dotnet test GameController.Companion.sln -c Release` |
| BeamNG mod | `beamng-mod/` | Lua (BeamNG GE and vehicle extensions) | `cd beamng-mod && luajit tests/run.lua` (needs `lua-socket`) |

All three speak one protocol, [protocol/PROTOCOL.md](../protocol/PROTOCOL.md) (v1). Every message carries `"v":1`. Change the protocol there first, then in all three parts.

### How input reaches the game

The app has two routes, and it can use both at once:

1. **Bluetooth HID.** The phone registers as "GameCtl Pad" (`android/app/.../hid/BluetoothHidLink.kt`). Its report descriptor (`android/core/.../hid/`) has a keyboard (report id 1) and a gamepad with 64 buttons and 8 axes (report id 2: X Y Z RX RY RZ SL0 SL1). No PC software is needed.
2. **Wi-Fi companion.** The app finds the PC by mDNS, with a UDP broadcast fallback, and pairs with a 6-digit PIN. It then uses a WebSocket on port 47800 for control and state, and UDP on 47801/47802 for low-latency values. The companion injects scancode keyboard input (SendInput), vJoy buttons and axes, an optional ViGEm Xbox pad, and macros. It talks to the BeamNG mod on 47803 and reads OutGauge on 4444.

`InputEngine` (`android/core/.../engine/InputEngine.kt`) picks a route for each control and tracks what each link is holding. It releases everything a link held when that link drops, and replays latches when the link comes back. Most of the review history is about these edge cases, and `CoreTest.kt` pins them. Run the tests after any change to it.

### Android module layout

- `:core` is plain JVM with no Android dependency: the model (`Profile`, `Page`, `Control`, `Action`), `InputEngine`, `Feedback`, `Protocol`, the HID descriptor, `BuiltInProfiles`, `LayoutOps`, and `ProfileJson` (import and export as `.gcprofile.json`, schema 2). `-PcoreOnly=true` builds and tests it without the Android SDK.
- `:app` holds the Compose UI (`ui/screens`, `ui/components`, `ui/theme`, `ui/icons`), `AppViewModel`, `BluetoothHidLink`, `CompanionClient`, and storage.

Control types are momentary, latching, switch, button group, slider, rotary, and joystick. A joystick is two spring-centred slider axes on the wire: `<id>.x` and `<id>.y`.

### Built-in profiles

`BuiltInProfiles.kt` has seven: Generic gamepad, Keyboard keys, BeamNG.drive, BeamNG.drive (Bluetooth pad), MudRunner, Bulldozer, and Utility vehicles (loader, excavator, crane, plough & tow). The machine profiles keep button numbers at 32 or below, and common jobs share one number across machines. The comment above `bulldozer()` lists them.

### Design

The visual language ("racing-game front end") is set out in [DESIGN.md](../DESIGN.md) and [PRODUCT.md](../PRODUCT.md). The companion follows the same tokens.

## Building and releasing

Everything is built by GitHub Actions ([.github/workflows/ci.yml](../.github/workflows/ci.yml)) on every push. Each part is uploaded as an artifact.

To publish a release, run the workflow by hand (**Actions > CI > Run workflow**) and set `release_tag`:

- A tag such as `beta-3` creates a prerelease.
- A tag such as `v1.0.1` creates the latest release, with `docs/RELEASE_NOTES.md` as its notes.

The release contains `GameCtl.apk`, the companion zip, the mod zip, `INSTALL.md`, `SMOKE_TEST.md`, and `SHA256SUMS.txt`.

### Signing (important)

- A `v*` release must be signed with the GameCtl release key. The workflow checks the APK's SHA-256 certificate fingerprint against the one pinned in `ci.yml` and refuses anything else.
- The key comes from three repository secrets: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_ALIAS` (`gamectl`). Gradle reads them as `GAMECTL_KEYSTORE`, `GAMECTL_KEYSTORE_PASSWORD`, `GAMECTL_KEY_ALIAS`, and optionally `GAMECTL_KEY_PASSWORD`. Without them, it falls back to the debug key.
- Android only installs an update over an app signed with the same key. Every future version must use this key. If the key is lost, users have to uninstall and reinstall.
- The keystore file and its password are never in this repository. The owner (ALI) holds them.
- **1.0.0 shipped with a test key**, by ALI's choice (`allow_test_key` was ticked), because the secrets were not set yet.
  A release key exists: ALI holds `gamectl-release.jks` and its password, and the workflow pins its fingerprint.
  For the first properly signed release, add the three secrets and don't tick `allow_test_key`. Users then uninstall
  and reinstall once, as RELEASE_NOTES explains. After that, updates install normally.

Before each release:

1. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Bump `Version` in `windows/Directory.Build.props`.
3. Bump the version in `beamng-mod/gamecontroller/mod_info/gamecontroller/info.json`.

## Testing on real hardware

Unit tests cannot cover Bluetooth pairing, vJoy, ViGEm, SendInput, or BeamNG in game. [SMOKE_TEST.md](SMOKE_TEST.md) is the end-to-end script for these, and takes about an hour. Run it before every release. `beamng-mod/EXPECTED.md` lists the log lines the mod should print in game.

## History and known limits

- `docs/review/` holds every review round, round 1 to rc3, with each part's replies. When a comment in the code cites an item id (A19, M1, B-3, U28, and so on), you can trace it back there. The 1.0 release candidate was signed off with 0 open comments in `rc3.md`.
- Known limits are listed in RELEASE_NOTES (Known limits) and `beamng-mod/README.md` (Known limits). They include:
  - There is no foreground service: leaving the app lets go of held controls.
  - The app's text is English only.
  - Some phones lack Bluetooth HID.
- Ideas that came up during review but are not built:
  - A foreground service, so play can continue in the background.
  - Localisation.
  - The `AutoMirrored` icon deprecations shown in the build log.
  - Moving to `actions/setup-java@v5` and newer Node-24 action versions.

## Where to look first

| Task | Start in |
|---|---|
| A new control type | `core/.../model/Model.kt`, then `InputEngine`, `Feedback`, `Profiles.kt` (defaults), the UI palette and inspector, and `PROTOCOL.md` |
| A new built-in profile | `core/.../profiles/BuiltInProfiles.kt`; the layout and round-trip tests check it automatically |
| A new BeamNG command or lamp | `beamng-mod/gamecontroller/lua/...`, the `beamng.*` ids in `PROTOCOL.md`, and the BeamNG profiles |
| A new companion output | `windows/src/Companion.Core/Input` and `Engine` |
| Connection or pairing bugs | `android/app/.../net/CompanionClient.kt` and `windows/src/Companion.Core/Net` |
