# GameCtl for BeamNG

This optional add-on lets the GameCtl phone app work hand in hand with BeamNG.drive. The phone already works with any Windows game as a controller. With this mod installed, BeamNG also:

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
3. Start BeamNG.drive and make sure **GameCtl for BeamNG** is enabled in **Mods** (it shows version 1.0.0 and author ALI).
4. Start the GameCtl companion on the same PC. The mod connects by itself within a few seconds and shows "Connected to the GameCtl companion". It keeps retrying every 3 seconds while the companion is closed, so the start order doesn't matter.

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
- **Bindings**: BeamNG keys controller maps by "vidpid" (product ID then vendor ID, in hex). vJoy is `bead1234`; the companion sends the vidpid of the phone's Bluetooth controller. Button N in the app is BeamNG's `button{N-1}`. Every vJoy device shares one vidpid, so the bindings apply to all of them. The mod never touches your own `.diff` files. About a second after writing, it checks that BeamNG has loaded the bindings, and only then answers `ok:true`. If it can't confirm (for example the controller isn't plugged in yet), it answers `ok:false` with `written:true` and a message pointing to Options > Controls.

## Known limits

- **A held game action can release in the next car.** If you hold a phone button that runs a game-engine action (an `action.<name>` binding such as Look back) and switch car with Ctrl+E while holding it, the release runs in the car you switched to, because that action always addresses the current player car. Horn, Start and Recover are not affected: they release in the car they were pressed in.
- **Trailers and props.** When you are in a vehicle with no engine or controllers (a trailer, a prop), the mod reports lamps and speed only. It leaves availability out rather than guessing, so the phone keeps its controls visible, and pressing them does nothing.
- **Winch.** Stock BeamNG has no winch, so `winch.active` is never reported.
- **One companion per PC.** The mod connects to the companion on this PC only (`127.0.0.1:47803`).

## Tests

The offline tests simulate the game engine VM, the companion (a real TCP server on port 47803) and four vehicles: a full car with its own vehicle VM, a trailer with its own vehicle VM and no powertrain or controllers, and two more cars that only record what is sent to them (one for vehicle switches, one whose Lua never answers):

```sh
# needs LuaJIT and LuaSocket, e.g. apt install luajit lua-socket
luajit tests/run.lua
```

The test mocks follow the mod's own understanding of the BeamNG APIs, so they can't prove those APIs match the game. That is what the in-game smoke test below is for.

## In-game smoke test

Run this once in BeamNG.drive with the PC companion running and the phone connected to it. It takes about 25 minutes. Control names below are exactly as they appear on the phone.

**Where to look.** Press the ` key (left of 1) to open the in-game console, and type `gameController` in its filter box to see only this mod's lines. The same lines are saved in `beamng.log` in the BeamNG user folder (in game: **Options > Other > Open user folder**).

**Cars.**
- **Gavril D-Series**: 4WD, low range, front and rear diff locks, ESC, cruise control. It has a part-time transfer case, so the phone hiding **Center diff** for it is expected.
- A police car for the light bar, for example the **Gavril Grand Marshal** police version.
- A car without ESC, for example the **Ibishu Pigeon**.

**Profiles.** Steps 1-3 and 5-12 use the **BeamNG.drive** profile. It has two pages: **Drive** and **4x4** (switch with the tabs at the top). Step 4 uses the **BeamNG.drive (Bluetooth pad)** profile.

1. **Connection.** Start BeamNG, then the companion. Within about 3 seconds a message says "Connected to the GameCtl companion" and the phone shows BeamNG as connected. Close the companion: a message says it disconnected. Start it again: it reconnects within about 3 seconds.
2. **What the mod found.** Get into each of the three cars once. Each time, the console shows a line that starts `vehicle <name>:` followed by `has.esc=…`, `has.cruise=…` and others. **Copy all three lines into your report**, whatever they say. (`unknown` means the mod couldn't tell, so the phone keeps that control visible.)
3. **Game actions.** In the D-Series, on the **Drive** page: switch the gearbox to manual (in game, Q), then press **Shift up** and **Shift down**. Hold **Recover** for two seconds and let go: the car rewinds while you hold it. Then try **Reset**, **Slow-mo** (on, then off), hold **Look back**, and on the **4x4** page **Center cam**. Each one does what its keyboard key does. In the console, a line "action failed in game engine Lua, running it in vehicle Lua (this is normal for some actions)" is fine. Write down any line that says "action failed in vehicle Lua" or "action not available".
4. **Bind in game.** This needs the companion connected and BeamNG running with the mod, and the companion must know the pad: either the phone is paired over Bluetooth or vJoy is installed. Open the **BeamNG.drive (Bluetooth pad)** profile and tap **Edit**. On a phone, tap the **gear icon** in the top bar (on a tablet the panel is already on the right), scroll to **BEAMNG** and tap **Bind in game**. A dialog lists every button and the BeamNG action it will get; confirm. About a second later a result row appears under the button:
   - "Bound N of N · HH:MM" with the device name means it worked.
   - "Saved N bindings … BeamNG could not confirm they are active" means the file was written, but BeamNG doesn't see the controller yet. Check that vJoy is installed or the phone is paired over Bluetooth, then tap **Bind in game** again.
   - Tap **Bind in game** twice quickly: the second attempt shows "BeamNG is still writing the last request", and the button stays usable.

   Then, in BeamNG, close and reopen **Options > Controls** (it only refreshes when opened), pick the vJoy device or "GameCtl Pad", and check its buttons are bound. Tap **Done**, go back to play, and press **Horn** and **Hazard** in this profile: they work. In the user folder, `settings/inputmaps/` has a new file ending in `_gamecontroller.json`, and your other files there are unchanged.
5. **4WD, diff locks, range.** In the D-Series, on the **4x4** page, press **4WD**, **Low range**, **Front diff** and **Rear diff**. The dash or the Drivetrain app (add it with **UI Apps**) shows each change, and the control's lamp on the phone follows. Turn **4WD** off and on again: it comes back in the same mode it was in.
6. **ESC.** In the D-Series, on the **4x4** page, press **ESC / TC** a few times: the game shows the ESC mode name at the top of the screen each time, and the phone's lamp is off for the mode that turns ESC off. In the Ibishu Pigeon, the `vehicle …` line from step 2 must say `has.esc=unknown`, not `has.esc=true`.
7. **Cruise control.** In the D-Series, on the **Drive** page, drive at about 50 km/h and press **Set**: the car holds its speed, and **Cruise** lights up. Turn the **Cruise speed** dial one click each way: the target changes by 5 km/h. Press **Cruise** to turn it off.
8. **Ignition and Start.** On the **Drive** page, press **Ignition** to turn the engine off, then hold **Start**: the engine cranks and starts, and stops cranking when you let go. The console shows `starter: mainController.setStarter` or `starter: ignition level fallback`. Write down which one.
9. **Rest of the Drive page.** **Low beam**, **High beam**, **Fog**, **Light bar** (police car), **Left**, **Right**, **Hazard**, **Horn**, **Park brake**, the **Gearbox** switch (P/R/N/D/S/M), **Cam prev**, **Cam next**, **Pause**. Each one works, and its lamp follows the game.
10. **Changing cars.** Switch car with **Ctrl+E**, spawn a new car from the vehicle menu, and reload the current one with **Ctrl+R**. After each, the phone's lamps match the new car within a second.
11. **Nothing stays pressed.**
    - Hold **Horn**, and while holding it switch car with **Ctrl+E**: the first car's horn stops.
    - Hold **Recover** in one car, and while holding it switch car with **Ctrl+E**: the first car stops rewinding.
    - Hold **Horn** and spawn a new car from the vehicle menu: no horn is left on.
    - Hold **Horn** and close the companion: the horn stops.
12. **Low frame rate.** Press **Ctrl+F** to show the frame rate, and get it under 20 fps (spawn a lot of traffic, or raise the graphics settings). Drive for two minutes. The phone's speed and lamps keep updating, and the console does not show "vehicle Lua went quiet, reloading the vehicle part".

Send the three `vehicle …` lines from step 2, plus each step that failed with the console lines around it.
