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

- **ESC and traction control** are one system in BeamNG with several modes per car (for example Regular, Sport, Off). `esc.active` and `tcs.active` are true when the current mode has ESC on, and `esc.mode` gives the mode's name. `esc.toggle` and `tcs.toggle` step to the next mode; the `esc` and `tcs` setters step until the mode matches.
- **Diff locks** are found by device name (front, centre or transfer case, rear). A toggle switches between "locked" and the diff's original mode.
- **4WD and range** are found by their powertrain device modes ("connected" or "disconnected", "high" or "low").
- **Winch**: stock BeamNG has no winch, so `winch.active` is never reported.
- **Bindings**: BeamNG keys controller maps by "vidpid" (product ID then vendor ID, in hex). vJoy is `bead1234`; the companion sends the vidpid of the phone's Bluetooth controller. Button N in the app is BeamNG's `button{N-1}`. Every vJoy device shares one vidpid, so the bindings apply to all of them.

## Tests

The offline tests simulate the game engine VM, one vehicle VM and the companion (a real TCP server on port 47803):

```sh
# needs LuaJIT and LuaSocket, e.g. apt install luajit lua-socket
luajit tests/run.lua
```

## Verify in game

These were checked against the BeamNG documentation and against working mods (BeamMP, a phone controller mod), but not yet inside a running game:

- that `extensions.cruiseControl` exposes `holdCurrentSpeed`, `setEnabled` and `changeSpeed`;
- the device-name heuristics for diffs, 4WD and range on modded cars;
- that a `<vidpid>_gamecontroller.json` file in the user folder's `settings/inputmaps` is merged like the game's own default maps (BeamMP uses the same approach for keyboard maps).
