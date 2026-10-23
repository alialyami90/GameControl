# GameCtl 1.0.0

The first full release of GameCtl: an Android phone or tablet as an extra control surface for Windows games.

## Download

| File | For |
|---|---|
| `GameCtl.apk` | The phone app (Android 9 or newer) |
| `GameCtl-companion-win-x64.zip` | The PC companion (Windows 10/11, 64-bit, no .NET install needed) |
| `gamecontroller.zip` | The BeamNG.drive mod |
| `INSTALL.md` | How to install and connect |
| `SMOKE_TEST.md` | One end-to-end test of everything |
| `SHA256SUMS.txt` | Checksums of the files above |

## Upgrading from a beta

1.0 is signed with a test key, like the betas, and that key differs between builds, so Android won't install 1.0 over a
beta. Export any profiles you made (card menu > **Export to file**), uninstall the beta, install 1.0, import them again,
and enter the companion's PIN once. A later version signed with a permanent release key will need the same steps once.

## What's in 1.0

**Two ways to connect, together or apart**
- **Bluetooth controller**: the phone pairs with Windows as "GameCtl Pad", a keyboard plus a 64-button, 8-axis gamepad. No PC
  software needed.
- **PC companion over Wi-Fi**: scancode keyboard input, vJoy buttons and axes, an optional virtual Xbox pad, macros, BeamNG
  commands, and live game state back to the phone. Found automatically on the network, paired once with a PIN.
- With both up, each control uses the best route, nothing stays pressed when a link drops, and the companion shows the phone
  on both links.

**Profiles and controls**
- Seven built-in profiles: Generic gamepad, Keyboard keys, BeamNG.drive, BeamNG.drive (Bluetooth pad), MudRunner, Bulldozer,
  and Utility vehicles (loader, excavator, crane, plough & tow).
- Controls: buttons, toggles, selectors, button groups, sliders and hydraulic levers, knobs, and pilot joysticks with two
  spring-centred axes. Lamps follow the game where it reports state.
- A layout editor with pages, drag to move and resize, an icon library, and export and import as `.gcprofile.json`.

**BeamNG.drive**
- Every vehicle function from the phone: lights, signals, hazards, horn, ignition and starter, gearbox modes, ESC, diff
  locks, 4WD and range, park brake, cruise control, cameras, recover and reset, slow motion and pause.
- Lamps follow the real vehicle, and controls the car doesn't have are dimmed.
- **Bind in game** maps the Bluetooth pad's buttons inside BeamNG in one tap.
- No mod? OutGauge telemetry still lights the basic lamps.

## Known limits

- Keep GameCtl on screen while playing. Leaving it lets go of held buttons and sliders; toggles stay on.
- Some phone makers remove Bluetooth HID support; on those phones use the companion over Wi-Fi.
- When the phone pairs over Bluetooth, Windows may switch the PC's sound to the phone. INSTALL explains how to turn that off.
- BeamNG: a held `action.*` command that targets the player's car is released in the new car after a vehicle switch
  (details in `beamng-mod/README.md`, Known limits).
- The app's text is English only.
