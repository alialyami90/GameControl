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

Run this once in BeamNG.drive with the PC companion and the phone connected, using the built-in BeamNG profile on the phone. It takes about 20 minutes.

**Where to look.** Press the ` key (left of 1) to open the in-game console and type `gameController` in its filter box to see only this mod's lines. The same lines are saved in `beamng.log` in the BeamNG user folder (in game: **Options > Other > Open user folder**). Each time you get into a car, the mod writes one line like `vehicle pickup: has.esc=true has.cruise=true ...`. That line says what the mod found on the car: `unknown` means it couldn't tell, so the phone keeps that control visible.

**Cars to use.** The Gavril D-Series (4WD, low range, diff locks, ESC, cruise control), a police car for the light bar (for example the Gavril Grand Marshal police version), and a car without ESC (for example the Bruckell Bastion, any older config).

1. **Connection.** Start BeamNG, then the companion. Within about 3 seconds a message says "Connected to the Game Controller companion" and the phone shows BeamNG as connected. Close the companion: a message says it disconnected. Start it again: it reconnects within about 3 seconds.
2. **Game actions (review item B1).** In the D-Series, press these phone controls: **Gear up** and **Gear down** (put the gearbox in manual first), hold **Recover** for two seconds then let go (the car rewinds while held), **Reset**, **Slow motion**, **Slower** and **Faster**, and hold **Look back**. Each one does the same as its keyboard key. In the console, a line "action failed in game engine Lua, running it in vehicle Lua (this is normal for some actions)" is fine. Write down any line that says "action failed in vehicle Lua" or "action not available".
3. **4WD, diff locks, range (B2).** In the D-Series, press **4WD**, **Low range**, and each **Diff lock** (front, center, rear). The car's dash or the Drivetrain app (add it with **UI Apps**) shows the change, and the phone's lamp for that control follows. Turn **4WD** off and on again: it comes back in the same mode it was in.
4. **Bindings (B3).** In the phone app's editor, use **Bind in game** for vJoy. After about a second the phone shows a result. Success reads "Bound N actions to vJoy device 1". If it reads "Saved N bindings ... BeamNG could not confirm they are active", make sure vJoy is installed and its device is enabled, then try again. Close and reopen **Options > Controls** (it only refreshes when opened), choose the vJoy device, and check the buttons are bound. Press a bound control on the phone with the output set to vJoy: the action runs. In the user folder, `settings/inputmaps/bead1234_gamecontroller.json` exists, and your own files there are unchanged.
5. **ESC (B4).** In the D-Series, press **ESC** on the phone: the game shows the new ESC mode name at the top of the screen, and the phone's ESC lamp goes out when the mode is the one with ESC off. Then get into the car without ESC and check its `vehicle ...:` console line: it must say `has.esc=unknown`, not `has.esc=true`.
6. **Cruise control (B5).** Drive the D-Series at about 50 km/h and press **Cruise set**: the car holds its speed and the phone shows cruise on at that speed. **Cruise +** and **Cruise -** change it by 5 km/h. **Cruise** off lets go.
7. **Starter (B6).** Press **Ignition** on the phone to turn it off, then hold **Starter**: the engine cranks and starts, and stops cranking when you let go. The console shows `starter: mainController.setStarter` (the main route) or `starter: ignition level fallback`. Write down which one.
8. **Rest of the profile.** Low beam, high beam, fog lights, light bar (police car), left and right signals, hazards, horn, parking brake, gearbox P/R/N/D/S/M, camera next and previous, pause. Each one works and its lamp on the phone follows.
9. **Changing cars.** Switch to another car (**Ctrl+E**), spawn a new one from the vehicle menu, and reload the current one (**Ctrl+R**). After each, the phone's lamps match the new car within a second.
10. **Nothing stays pressed.** Hold **Horn** on the phone and switch car with **Ctrl+E** while holding it: the horn of the first car stops. Hold **Horn** again and close the companion: the horn stops.
11. **Slow motion soak.** Turn on slow motion at its slowest setting and drive for two minutes. The phone's speed and lamps keep updating, and the console does not show "vehicle Lua went quiet, reloading the vehicle part".

Send the list of steps that failed, with the console lines around each one.
