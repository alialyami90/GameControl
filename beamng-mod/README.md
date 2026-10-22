# Game Controller mod for BeamNG.drive

This optional add-on lets the Game Controller phone app work hand in hand with BeamNG.drive. The phone already works with any Windows game as a controller. With this mod installed, BeamNG also:

- reports what the car is doing (lights, signals, ESC, diff locks, 4WD, gear, speed, and more), so buttons on the phone light up and controls the car doesn't have are hidden;
- runs car functions directly when you press a button on the phone, without needing a key binding;
- binds the phone's vJoy or Bluetooth controller buttons to BeamNG actions for you, when you pick "Apply bindings in BeamNG" in the app.

## Install

1. Build the zip (or take `gamecontroller.zip` from a release):
   ```sh
   sh build.sh           # writes dist/gamecontroller.zip
   ```
2. Copy `gamecontroller.zip` into your BeamNG user folder's `mods` folder. That is usually
   `%LOCALAPPDATA%\BeamNG.drive\<version>\mods` (older versions use `Documents\BeamNG.drive\mods`). In game you can open it from **Options > Other > Open user folder**.
3. Start BeamNG.drive and make sure **Game Controller** is enabled in **Mods**.
4. Start the Game Controller companion on the same PC. The mod connects by itself within a few seconds and shows "Connected to the Game Controller companion". It keeps retrying every 3 seconds while the companion is closed, so the start order doesn't matter.

To remove it, delete the zip or disable it in **Mods**. If you applied bindings from the app, they live in `settings/inputmaps/<vidpid>_gamecontroller.json` in the user folder; delete that file to remove them, or change them as usual in **Options > Controls**.

## How it works

| Part | File | Runs in |
|---|---|---|
| Loader | `scripts/gamecontroller/modScript.lua` | Loads the game engine extension when the mod is active |
| Link | `lua/ge/extensions/gameControllerLink.lua` | Game engine Lua. TCP client to `127.0.0.1:47803`, commands, bindings, game state (pause, slow motion, camera) |
| Vehicle | `lua/vehicle/extensions/gameControllerVehicle.lua` | Player vehicle Lua. Reads electrics, powertrain devices and the ESC controller, applies vehicle commands |

The link loads the vehicle part into whatever vehicle you drive, and loads it again after a vehicle switch, a new spawn, or a reload. The vehicle part checks state 10 times a second and only sends what changed. A full snapshot goes out after connecting, after a vehicle switch, and when the companion sends `state.request`.

Commands use the most direct route that is reliable:

- Car functions with a clear Lua API (lights, signals, hazards, horn, ignition and starter, parking brake, diff locks, 4WD, range, gearbox mode, cruise control, ESC modes) are set directly in vehicle Lua, so the on/off setters are exact.
- Functions that are best left to the game (gear up and down, recover, reset, slow motion, and any `action.<name>` command) run the game's own input action. The mod reads the action definitions from the installed game (`lua/ge/extensions/core/input/actions/*.json` and the vehicle's `input_actions*.json`) and runs the same code a key press would run, so it follows the game version you have.
- If a direct route is missing on a vehicle, the mod falls back to the matching action (for example `toggle4WDStatus` or `toggleESCMode`), and the other way round.

The protocol is in [`protocol/PROTOCOL.md`](../protocol/PROTOCOL.md), section "BeamNG mod link". The mod reports its exact command list in the `commands` message.

### Notes on specific features

- **ESC and traction control** are one system in BeamNG with several modes per car (for example Regular, Sport, Off). `esc.active` and `tcs.active` are true when the current mode has ESC on, and `esc.mode` gives the mode's name. `esc.toggle` and `tcs.toggle` run the game's own `toggleESCMode` action (so newer drive-mode systems work too). The `esc` and `tcs` setters step through the modes until ESC is on or off. When the car has no readable ESC controller, `has.esc` is left out instead of being sent as false.
- **Diff locks** are found by device name (front, centre or transfer case, rear). A toggle switches between "locked" and the mode the diff had before. Modes change through `powertrain.setDeviceMode` when the game has it, so the game's own UI follows.
- **4WD** counts as on in any mode other than "disconnected"; switching it back on restores the mode it had. **Range** is "low" or "high".
- **Availability (`has.*`)** is only sent when the mod could tell. `has.fog` and `has.light_bar` come from the vehicle's light setup, `has.cruise` from the cruise control extension, and diff, 4WD and range availability only when the powertrain scan found the car's differentials. Anything the mod can't tell is left out, so the phone shows that control.
- **Starter** uses the vehicle controller's `setStarter` and falls back to holding the ignition key in the start position.
- **Winch**: stock BeamNG has no winch, so `winch.active` is never reported.
- **Bindings**: BeamNG keys controller maps by "vidpid" (product ID then vendor ID, in hex). vJoy is `bead1234`; the companion sends the vidpid of the phone's Bluetooth controller. Button N in the app is BeamNG's `button{N-1}`. Every vJoy device shares one vidpid, so the bindings apply to all of them. The mod never touches your own `.diff` files. About a second after writing, it checks that BeamNG has loaded the bindings, and only then answers `ok:true`. If it can't confirm (for example the controller isn't plugged in yet), it answers `ok:false` with `written:true` and a message pointing to Options > Controls.

## Tests

The offline tests simulate the game engine VM, one vehicle VM and the companion (a real TCP server on port 47803):

```sh
# needs LuaJIT and LuaSocket, e.g. apt install luajit lua-socket
luajit tests/run.lua
```

The test mocks follow the mod's own understanding of the BeamNG APIs, so they can't prove those APIs match the game. That is what the in-game smoke test below is for.

## In-game smoke test

Run this once in BeamNG.drive with the companion and the phone connected. Use a stock car with 4WD and low range, ESC and cruise control, such as the Gavril D-Series or the Ibishu Hopper. Open the console with the ` key and filter for `gameController` to see the mod's log lines.

1. **Connection.** Start BeamNG, then the companion. Within about 3 seconds you see "Connected to the Game Controller companion" and the phone shows BeamNG as connected. Close the companion: the toast says it disconnected. Open it again: it reconnects within about 3 seconds.
2. **Actions (B1).** On the phone, use gear up and gear down (any car in manual mode), recover (hold), reset, slow motion and its faster and slower steps, and look back. Each one does the same as its key. In the console, no "action failed" or "action not available" lines appear for these.
3. **4WD, diffs, range (B2).** In the D-Series: toggle 4WD, low range and each diff lock. The car's state changes (check the Drivetrain UI app or the dash), and the phone's indicators follow. Switch 4WD off and on again: it comes back in the same mode it had.
4. **Bindings (B3).** In the app, apply BeamNG bindings for vJoy. The phone reports success and Options > Controls shows the vJoy buttons bound to those actions. Press a bound vJoy button: the action runs. In the user folder, `settings/inputmaps/bead1234_gamecontroller.json` exists and your own `.diff` files are unchanged.
5. **ESC (B4).** Toggle ESC from the phone: the game shows the new ESC mode name, and the phone's ESC indicator turns off for the mode with ESC off. In a car without ESC (for example the Bruckell Bastion), the ESC control is not hidden by mistake. On a car with drive modes (for example the Hirochi Sunburst), toggling ESC still changes the mode.
6. **Cruise control (B5).** Drive at about 50 km/h, press cruise set: the car holds speed and the phone shows cruise on with that speed. Cruise up and down change the target by 5 km/h. Cruise off releases it.
7. **Starter (B6).** Turn the ignition off from the phone, then hold the starter: the engine cranks and starts, and stops cranking when you release. Ignition on and off follow on the phone.
8. **Rest of the profile.** Lights low and high, fog lights, light bar (police car), signals, hazards, horn, parking brake, gearbox P/R/N/D/S/M, camera next and previous, pause. Each works and its indicator follows.
9. **Vehicle changes.** Switch to another car (Ctrl+E), spawn a new one, and reload one with Ctrl+R. After each, the phone's indicators match the new car within a second, and controls the car doesn't have are dimmed.
10. **Release on disconnect.** Hold the horn from the phone and close the companion: the horn stops.

Report anything that fails with the console lines around it.
