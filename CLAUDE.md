# CLAUDE.md

This file guides Claude Code (claude.ai/code), or any developer, working in this repository.

## Status

Version 1.0.0 has been released. The Reviewer signed off on it with 0 open comments (`docs/review/rc3.md`). The project overview for a new developer is in [docs/HANDOVER.md](docs/HANDOVER.md). Read it first.

## What this project is

GameCtl is an Android app that works as an extra control surface for Windows games. It has buttons, toggles, selectors, sliders, knobs, and joysticks, organised into per-game profiles.

- It reaches the PC as a Bluetooth HID device ("GameCtl Pad"), through the Windows companion over Wi-Fi, or both.
- A BeamNG.drive mod reports vehicle state back, so lamps light and controls the car doesn't have are dimmed. It can also bind the pad's buttons in game.

## Commands

| What | Command |
|---|---|
| Android core tests | `cd android && ./gradlew :core:test` (no SDK: add `-PcoreOnly=true`) |
| Android APKs | `cd android && ./gradlew :app:assembleDebug :app:assembleRelease` |
| Windows tests | `cd windows && dotnet test GameController.Companion.sln -c Release` |
| Windows publish | `cd windows && ./build.ps1 -Output ../out/companion` |
| BeamNG mod tests | `cd beamng-mod && luajit tests/run.lua` (needs `lua-socket`) |
| BeamNG mod zip | `sh beamng-mod/build.sh` |
| Release | Run the CI workflow by hand with `release_tag` (see HANDOVER, Building and releasing) |

CI (`.github/workflows/ci.yml`) runs all three test suites on every push.

## Layout

- `android/core`: the pure-JVM model, input engine, protocol, HID descriptor, and built-in profiles. All unit tests live here.
- `android/app`: the Compose UI, `AppViewModel`, `BluetoothHidLink`, and `CompanionClient`.
- `windows/src/Companion.Core`: networking, input injection (SendInput scancodes, vJoy, ViGEm), the BeamNG link, OutGauge, and config.
- `windows/src/Companion.App`: the Avalonia UI.
- `windows/src/Companion.Cli`: a headless runner.
- `beamng-mod/gamecontroller`: the mod, with a GE extension and a vehicle extension. `beamng-mod/tests` is a LuaJIT harness with fake vehicle VMs.
- `protocol/PROTOCOL.md`: the shared v1 protocol and profile file format. Change it before changing any of the three parts.
- `docs/`:
  - `INSTALL.md` (users), `SMOKE_TEST.md` (manual end-to-end test), `RELEASE_NOTES.md`, and `HANDOVER.md`.
  - `review/`: every review round and its replies.
- `DESIGN.md` and `PRODUCT.md`: the visual design system and product brief.

## Rules worth keeping

- Comments in the code cite review item ids (A19, M1, B-3, and so on). `docs/review/` explains each one. Keep the regression test when you touch that code.
- Keyboard output uses scancodes, not virtual keys, because DirectInput games ignore VK-only events.
- Machine profiles keep joystick buttons at 32 or below, with one number per shared job.
- Never commit signing keys or passwords. A `v*` release must be signed with the GameCtl release key, whose fingerprint is pinned in `ci.yml`. 1.0.0 was the exception: it was released with a test key, as HANDOVER explains.
- Every message carries the protocol version. Bump `ProfileJson.SCHEMA` when the profile format changes.
