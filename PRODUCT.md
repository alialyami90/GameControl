# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

PC gamers on Windows who play any game, with simulation and driving games (BeamNG.drive fully supported, MudRunner and similar) as the flagship case, and run out of buttons on a gamepad, wheel, or keyboard. They prop an Android phone or tablet next to the screen or wheel and reach over to it mid-drive, often without looking straight at it. Between sessions they build and tweak layouts per game. *(inferred from the brief: no interview round was possible)*

## Product Purpose

A generic HID-style extra controller for any Windows game (the PC sees keyboard, virtual joystick, or virtual Xbox pad input): buttons, switches, sliders, rotaries and indicator lamps beyond a physical gamepad, organised in per-game profiles. Success is a driver who can flick the hazards, lock the diff or cycle the camera from the phone in under a second, glance-confirm it worked from a lit indicator, and never fight the editor to set it up.

## Positioning

Works with any Windows game as a generic input device. Goes further for BeamNG.drive, which is fully supported two-way: a BeamNG mod reports vehicle state, so controls appear only when the vehicle has the feature and indicators light from real game state, not just local latch state. Bindings can be applied in-game automatically.

## Operating Context

- Phone or tablet in landscape on a stand, desk, or wheel-rim mount, at arm's length, glanced at peripherally while eyes stay on the main monitor.
- Usually a dim room lit by the game monitor.
- Windows companion app on the same LAN does the input injection (scancodes, vJoy, optional ViGEm).
- Two modes: play (the control surface) and edit (grid layout editor with free placement, multiple pages).

## Capabilities and Constraints

- Control types: momentary button, latching toggle, multi-position switch, slider/axis with optional spring-return, rotary/encoder, radio button group. Every control can carry a feedback indicator (local or game-driven).
- Actions: key/combo, virtual joystick button/axis, macro, game-native command.
- Generic first: custom profiles for any game are first-class, not an afterthought. Built-in profiles: BeamNG (lights, signals, hazards, horn, ignition/starter, gearbox modes, ESC/TC, diff locks, 4WD/range, parking brake, cruise, camera, recovery/reset, slowmo/pause) and MudRunner (diff lock, AWD, gears, winch, map, camera). Custom profiles; JSON import/export.
- Curated icon library for presets; users pick from it.
- Stack: Kotlin + Jetpack Compose (from project plan).

## Brand Commitments

- The UI should be inspired by gaming and driving games (user's explicit brief).
- Product name not yet decided; working name "Game Controller".

## Evidence on Hand

None. No logo, screenshots, or user quotes exist. Do not fabricate download counts, reviews, or compatibility claims beyond the games named above.

## Product Principles

1. Glanceable over readable: state must be legible from peripheral vision at arm's length.
2. One reach, one action: every play-mode control is a big, forgiving touch target with unmistakable feedback (visual and haptic).
3. Game truth beats local guess: when the game reports state, the indicator shows the game's state.
4. Editing is a workshop, playing is a cockpit: two clearly separate modes.

## Accessibility & Inclusion

48dp minimum targets (play controls much larger), colour never the only state signal (indicators also change shape/label), honour system font scale in menus and the Remove animations setting.
