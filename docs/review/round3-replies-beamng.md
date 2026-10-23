# Round 3 replies: BeamNG mod

The offline suite now has 150 checks and passed 5 runs in a row. `dist/gamecontroller.zip` has been rebuilt.

## MUST
- **B26:** `releaseHeld(veh)` now releases a held action in two ways. It runs the action's release, pinned to `veh`: with `fixedVehicle`, a nil `veh` drops vehicle-Lua snippets instead of falling through to the new car. For commands that also have a vehicle route (recover, starter, horn), it also sends the vehicle-side release straight to the old car (`recovery.stopRecovering()`, idempotent). `releaseHeld(nil)` sends nothing to any vehicle. The test mock now declares `recover_vehicle` as the game ships it: GE Lua addressing `be:getPlayerVehicle(0)`. A new test holds Recover in car 7, switches to car 8, and checks that car 7 gets `stopRecovering`. Limit: a held `action.<name>` whose GE code addresses "the player's car" can't be aimed at the old car, because the mod has no vehicle-side equivalent for arbitrary actions. Its release runs in the new car, which is harmless for the stock hold actions (look_back is camera-only).
- **B28:** The smoke test is rewritten against `BuiltInProfiles.kt`. Changes:
  - Controls: Shift up/down, Slow-mo, Set plus the Cruise speed dial, Start, Center cam.
  - Pages: Drive and 4x4. ESC / TC and the diffs, 4WD and range are on 4x4.
  - Bind in game runs from the "BeamNG.drive (Bluetooth pad)" editor, and the step explains both success texts (vJoy and Bluetooth).
  - Cars: the Pigeon is the no-ESC car, and Center diff being hidden on the D-Series is called out as expected.
  - The tester always pastes the three `vehicle …` lines (step 2).
  - New: hold Recover across Ctrl+E, and spawn while holding Horn (step 11).
  - Step 12 is a low frame rate check (Ctrl+F, under 20 fps).

## Partial from round 2
- **B22:** Action-route holds are now released in the old car too (B26). Vehicle-route holds were already released there.

## SHOULD
- B25: Every exit path now goes through `onDisconnected`, which releases holds and clears `pendingBindings`: send errors in `flush()`, send-buffer overflow in `send()`, and the version-mismatch close. It is idempotent (it returns when the socket is already gone). No test was added for the send-error path, because it can't be forced reliably over loopback.
- B27: An ESC controller counts if `c.toggleESCMode` is a function, so a metatable `__index` works, and a key no controller has is not a function, so the nop dummy is rejected. The check runs in pcall. A new test puts the ESC API behind a metatable and checks that `has.esc` is true; the dummy test still gives unknown.
- B29: The `alreadyBound` message no longer names the user's `.diff` as the cause: it now reads "Bound N actions to … (already bound)". Please change PROTOCOL.md:198 the same way. Also, PROTOCOL.md:218 still says "flips state on the rising edge" and needs to match :110.

## After round 3
- New info log lines are `Game Controller mod 1.0.0 loaded, looking for the companion on 127.0.0.1:47803` (onExtensionLoaded) and `command <name> <value>` (each command received). They exist so the combined smoke test can quote what the console should show. Every line is listed in `beamng-mod/EXPECTED.md`. The suite still passes 150/150 and dist has been rebuilt.
