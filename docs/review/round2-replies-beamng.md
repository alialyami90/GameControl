# Round 2 replies: BeamNG mod

The offline suite now has 145 checks and passed 5 runs in a row. `dist/gamecontroller.zip` has been rebuilt. The smoke test in `beamng-mod/README.md` has been rewritten for a non-developer and now has 11 steps.

## MUST
- **B21:** The ESC lookup no longer uses `getControllerSafe`, and the remaining candidates must pass `rawget(c, "toggleESCMode")`. The suite now mocks a nop-everything `getControllerSafe` dummy and a car without ESC; that car gives `has.esc` omitted and `esc.active` omitted. The mod also logs one line per vehicle, `vehicle <dir>: has.esc=... has.cruise=... ...`, with `unknown` for omitted keys. Smoke step 5 asks for `has.esc=unknown` on the car without ESC.
- **B22:** `onVehicleSwitched` first releases every held command in the *old* vehicle (vehicle-route holds go to the old car's Lua, action-route holds run their release with the old car as target), then clears `heldActions`. `onVehicleSpawned` for the player car clears holds, because that car's Lua is replaced. A test checks that horn is released in car A and nothing is sent to car B. Smoke step 10.

## Partials from round 1
- **B1:** See B18/B19. Queued vehicle-Lua action code now reports failures back, and the "normal" hedge log line is spelled out in smoke step 2.
- **B3:** See B20.
- **B9:** Holds are released on disconnect and now on vehicle switch (B22).
- **B10:** See B23.

## SHOULD
- B18: Vehicle-Lua action code now runs inside a `loadstring` + `pcall` wrapper. On failure it logs the error and calls `gameControllerLink.onActionFailed(token)` in GE, which runs the caller's fallback once (handlers expire after 10 s). This covers explicit `vlua` actions and the GE-to-vlua hedge. Wired fallbacks:
  - `oneShots` with `vehicle=true` fall back to the native vehicle route.
  - Hold commands switch to the vehicle route if the press failed and the button is still held.
  - `slowmo` falls back to `simTimeAuthority.set`.
  - The ESC/4WD/diff/range fallbacks requested from vehicle Lua fall back to a direct toggle.

  A test makes `shiftUp` throw in vehicle Lua and checks that `mainController.shiftUpOnDown` runs.
- B19: Entries from `getActiveActions()` are shallow-copied before `_ctx`/`_ctxExplicit` are added. `hasAction` is used again (hold and one-shot routing).
- B20:
  - A second `bindings.apply` while one is being confirmed now gets `ok:false, busy:true` ("Still applying the previous bindings. Try again in a second."), so neither request goes unanswered (tested).
  - `pendingBindings` is cleared on disconnect.
  - If the same pairs were already live before the write, the success message says "(they were already bound in the game)" and adds `alreadyBound:true`.
  - The Lead developer has been asked to add `written`, `count`, `unknown`, `device`, `busy` and `alreadyBound` to PROTOCOL.md.
- B23: `HEARTBEAT_FRAMES` is now 30 and `VEHICLE_TIMEOUT` is now 5 s. A test runs vehicle `updateGFX` with dt = 0 for 140 frames and checks there is no reload. The mod logs "vehicle Lua went quiet, reloading the vehicle part" when it does reload (smoke step 11).
- B24: Smoke test rewritten:
  - names the exact phone controls and which cars to use;
  - says where the console filter and `beamng.log` are, and that the hedge info line is normal;
  - quotes both bindings result messages and says to reopen Options > Controls;
  - checks `has.esc` on a car without ESC;
  - logs which starter route ran;
  - adds the switch-while-holding-horn step and a slow-motion soak.

- Toggle rule (round 2 spec change): every forwarded `*.toggle` now flips the feature whatever its value (the companion sends rising edge only for momentary, every event for latching), covering pause and slowmo too; tested, 147 checks. (Noted by the Lead developer on behalf of the mod dev.)
