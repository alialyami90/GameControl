# Round 1 replies: BeamNG mod

The offline suite has 137 checks and passed 5 runs in a row (it was flaky before B7). `dist/gamecontroller.zip` has been rebuilt. Each of B1-B6 has a step in the in-game smoke test (`beamng-mod/README.md`, "In-game smoke test").

## MUST
- **B1:** The BeamNG docs (modding/input/actions) say a missing `ctx` means `tlua` for regular actions and `vlua` for vehicle-specific ones, so the defaults stay. Hedges: if an action without an explicit ctx fails in GE Lua, the mod runs it once in vehicle Lua. `ui`/`ts`/`slua` actions are skipped. `runAction` returns false when an action is missing or failed, and the vehicle-side fallback then runs. `/vehicles/common/` is now scanned too. When the game exposes `core_input_actions.getActiveActions()`, that list wins. Smoke test step 2.
- **B2:** 4WD is on whenever its mode is not "disconnected", and "on" restores the last engaged mode. Diff, 4WD and range modes change through `powertrain.setDeviceMode(name, mode)` when it exists, otherwise through `device:setMode`. Step 3.
- **B3:** The file name follows the documented `identifierSuffix.json` scheme for mod defaults (0.25+). The reload uses `core_input_bindings.onFileChanged`, the same call BeamMP uses for its keyboard map. Users' `.diff` files are never touched. About 1 s after writing, the mod checks `core_input_bindings.bindings` for the vidpid and our control/action pairs. It reports `ok:true` only if they are all there; otherwise it sends `ok:false` with `written:true` and a message. Step 4.
- **B4:** The ESC controller is now looked up with `getController('esc')`, then `getControllerSafe('esc')`, then a scan, and must export `toggleESCMode`. `escEnabled` is checked as a boolean. `has.esc`/`has.tcs` are left out when not found, never sent as false. Toggles run the game's `toggleESCMode` action first, which covers drive-mode cars, with a direct fallback. Step 5.
- **B5:** The mod loads `cruiseControl` explicitly (`extensions.load`) and only uses it if it has `setEnabled`. State comes from the electrics, or from `getConfiguration()` when those keys are missing. `has.cruise` is left out when it isn't loaded. Step 6.
- **B6:** The starter uses `controller.mainController.setStarter(true/false)`, falling back to ignition level 3 then 2. Step 7.

## SHOULD
- B7: `tcp-nodelay` is now set in `onConnected()` on the connected socket, and the test client sets it too.
- B8: `pollConnecting` calls `connect` again each frame. Refused shows up at once on every platform, so a retry happens every 3 s (covered by a test).
- B9: Vehicle-route holds (`horn`, `starter`, the `recover` fallback) are tracked in `heldActions` and released on disconnect (covered by a test).
- B10: The vehicle side also counts frames, so state and heartbeats flow while paused or in slow motion. GE doesn't count silence while paused.
- B11: `gearbox.mode` no longer switches arcade to realistic.
- B12: The manual path uses `mainController.shiftToGearIndex` first, with `setGearIndex` as fallback. `AUTO_GEAR_INDEX` is the table BeamMP uses to sync gears in multiplayer.
- B13: `has.fog`/`has.light_bar` are detected from the vehicle data (props, glowMap, flexbodies) and only sent when found. Diff, 4WD and range `has.*` are sent only when the powertrain scan found differentials.
- B14: `game.slowmo` is not updated while paused.
- B15: `require("socket")` is wrapped in pcall, with a clear error log line, and the link stays off.
- B16: GE reaches the vehicle extension through `extensions.gameControllerVehicle`.
- B17: The test receive loop keeps partial lines. New tests cover refused connect and reconnect, a split message, holds released on disconnect, action context fallback, unconfirmed bindings, and the setStarter path.
