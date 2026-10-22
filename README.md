# GameCtl: an Android control surface for Windows games

Turn an Android phone or tablet into an extra controller for any Windows game: buttons, toggles, switches, sliders,
rotaries and indicator lamps beyond your gamepad, organised in per-game profiles. BeamNG.drive is fully supported
both ways (live vehicle state on the phone, game commands, in-game auto-binding).

| Part | Folder | What it does |
|---|---|---|
| Android app | `android/` | Profiles, layout editor, play surface. Acts as a **Bluetooth HID controller** ("GameCtl Pad": keyboard + 64-button / 8-axis gamepad) and/or talks to the companion over Wi-Fi. |
| Windows companion | `windows/` | Optional tray app: scancode keyboard, vJoy, virtual Xbox pad (ViGEm), macros, BeamNG commands and game feedback to the phone. |
| BeamNG mod | `beamng-mod/` | Reports vehicle state, runs commands, writes in-game bindings. |
| Protocol | `protocol/PROTOCOL.md` | The shared, versioned phone / PC / mod protocol. |

Full install and build steps: see `docs/INSTALL.md` (added with the first release).
