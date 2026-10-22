# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Greenfield. No code exists yet, so there are no build, lint, or test commands. Replace the "Planned" sections below with real commands and structure as each component is scaffolded. Anything marked *(verify)* is an assumption that has not been checked against the real game or library.

## What this project is

An Android app that works as an extra control surface for Windows games. It adds buttons, switches, sliders, and other controls beyond a physical gamepad, with per-game profiles. Phase 2 adds a BeamNG.drive mod that talks to the app both ways: it reports game state so the app can show or hide relevant controls and light up indicators, and it applies key bindings in the game automatically.

## Planned architecture

Android cannot inject input into Windows by itself, so there are three components:

1. **Android app** (`android/`): Kotlin and Jetpack Compose. It owns the layout editor, profiles, the icon library, and the runtime control surface. It sends control events and receives feedback.
2. **Windows companion** (`windows/`): a small tray app, probably C# / .NET. It receives events from the phone and turns them into input the game can see:
   - Keyboard: `SendInput` with **scancodes**, not virtual keys, because many DirectInput games ignore VK-only events.
   - Extra buttons and axes: a virtual joystick driver such as vJoy (up to 128 buttons and 8 axes per device) *(verify which fork is maintained)*. This is how controls go "beyond the gamepad" without using up keyboard keys.
   - Optional virtual Xbox pad through ViGEm. ViGEmBus is archived upstream, so treat it as optional.
   - It also relays game feedback (from the BeamNG mod or telemetry) back to the phone.
3. **BeamNG mod** (`beamng-mod/`, phase 2): a Lua extension. It sends vehicle and game state to the companion, takes commands from it, and can write input bindings so that picking "traction control" in the app binds it in the game. BeamNG separates game-engine Lua from per-vehicle Lua (vlua), so state has to be gathered from the right context *(verify the APIs and the binding-file format against the current BeamNG version)*. BeamNG's OutGauge UDP output is a fallback telemetry source when the mod is not installed.

### Protocol (phone to companion)

- Discovery over mDNS/NSD on the LAN, then pairing.
- Low-latency control events over UDP. Each event carries the control id, its value, and a sequence number.
- Profile sync, feedback/state, and the BeamNG mod channel over WebSocket/TCP.
- Keep one versioned schema shared by all three components, for example in `protocol/`. Every message carries a protocol version.

### Core domain model (Android)

- **Profile**: belongs to one game. It holds pages, and each page holds controls. Profiles include the built-in BeamNG and MudRunner profiles as well as user-made custom profiles. They can be imported and exported as JSON.
- **Control types**: momentary button, latching (toggle) button, multi-position switch, slider or axis (with optional spring-return to centre), rotary or encoder, and button group (radio). Every control can show a feedback indicator. An indicator is driven locally (latch state) or by game state when the mod or telemetry reports it.
- **Action**: what a control does. It can be a key or key combo, a virtual joystick button or axis, a macro, or a game-native command (BeamNG mod). Presets bundle an action, a label, and an icon from the icon library.
- **Layout**: a grid with free placement. Controls can be added, removed, moved, and resized, and there are multiple pages. Edit mode and play mode are separate.

## Requirements to keep in mind

- The BeamNG profile should cover most vehicle functions: lights, signals, hazards, horn, ignition and starter, gearbox modes, ESC and traction control, differential locks, 4WD and range, parking brake, cruise control, camera, recovery and reset, and the slowmo and pause tools. MudRunner needs diff lock, AWD, gear selection, winch, map, and camera controls.
- Built-in presets need proper icons, so ship a curated icon set and let users pick from the library.
- The user mentioned the "Rojx" app as a possible reference for the BeamNG integration *(not yet researched)*.
