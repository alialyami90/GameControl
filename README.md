# GameCtl: an Android control surface for Windows games

**Version 1.0.0.** Turn an Android phone or tablet into an extra controller for any Windows game: buttons, toggles,
switches, sliders, pilot joysticks, rotaries and indicator lamps beyond your gamepad, organised in per-game profiles.
BeamNG.drive is fully supported both ways (live vehicle state on the phone, game commands, in-game auto-binding), and
there are built-in profiles for a generic gamepad, keyboard keys, MudRunner, a bulldozer and other utility vehicles.

![The BeamNG.drive profile on a phone](docs/images/play-beamng.png)

## Download

Get the latest release from **https://github.com/alialyami90/GameControl/releases**: `GameCtl.apk` (phone),
`GameCtl-companion-win-x64.zip` (PC companion), `gamecontroller.zip` (BeamNG mod). Then follow
[docs/INSTALL.md](docs/INSTALL.md).

**Requirements:** Android 9 or newer; Windows 10 or 11, 64-bit (the companion is self-contained, no .NET install needed);
Bluetooth on the PC for the Bluetooth route; the same Wi-Fi network for the companion route.

## What's in this repository

| Part | Folder | What it does |
|---|---|---|
| Android app | `android/` | Profiles, layout editor, play surface. Acts as a **Bluetooth HID controller** ("GameCtl Pad": keyboard + 64-button / 8-axis gamepad) and/or talks to the companion over Wi-Fi. |
| Windows companion | `windows/` | Optional tray app: scancode keyboard, vJoy, virtual Xbox pad (ViGEm), macros, BeamNG commands and game feedback to the phone. |
| BeamNG mod | `beamng-mod/` | Reports vehicle state, runs commands, writes in-game bindings. |
| Protocol | `protocol/PROTOCOL.md` | The shared, versioned phone / PC / mod protocol. |

Install and usage: [docs/INSTALL.md](docs/INSTALL.md). Full end-to-end check: [docs/SMOKE_TEST.md](docs/SMOKE_TEST.md). Taking over development: [docs/HANDOVER.md](docs/HANDOVER.md).
What changed: [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md). Companion build details: `windows/README.md`.

## License

No open-source license has been chosen: all rights are reserved by the project owner (ALI). You may install and use the
released builds; ask before redistributing or reusing the code.
