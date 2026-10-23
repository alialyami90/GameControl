# Round 6 review (dev 5d8f647: pilot joystick, Bulldozer and Utility vehicles)

**Verdict: nothing blocks.** The joystick design is sound. It turns into two spring-centred bipolar sliders, `<id>.x` and `<id>.y`, so the protocol, the companion and the mod need nothing new. There are two small fixes in the built-in machine profiles, listed first below. Not run here: the Android UI on a device, and binding in a real machine game.

## Checked and fine
- **Release on cancel or dispose.** PilotJoystick sends 0 on both axes in a `finally` around the gesture, so a page switch, a removed control, or `active` turning false mid-drag all centre the stick. `DisposableEffect` covers leaving composition. Lifting outside the pointer's id breaks the loop and releases too. A tap inside the dead zone sends 0.
- **Engine, routes and replay for .x/.y.** `findControl`, `LayoutOps.findControl`, `hidRoutedIds`, `resetAxesState` and `profile.sync` all go through `Joystick.expand` / `find`. That means routeOf, lastValue, the route-flip park (sliders are parked), replayHeld and rehomeLatches see two ordinary sliders. The axes go over UDP like any slider, with the 250 ms resend and the companion's 1 s auto-release. The test joystickDrivesTwoAxesAndSyncsAsTwoSliders covers HID and the sync.
- **Editor.** The Inspector edits `action` and `actionY` with `axisOnly`. Copy-based updates keep `actionY`. minSpan is 2×2 in both core and the app, and the default control is X/Y on vJoy 1.
- **Grids.** I ran a script over all five machine pages: no overlaps, nothing off the 8×4 grid, and no axis used twice on a page. Dozer, Loader, Excavator and Crane fill 32 of 32 cells, and Plough & tow fills 29.
- **Round 5 R5-2 is fixed.** A user's Disconnect now survives a Bluetooth reconnect (9d0c845).

## SHOULD
- **R6-1. Crane: Hook view and Rear view both send button 12** (`cr_hook_cam`, `cr_rear_cam` in BuiltInProfiles.kt). One of them can never be bound separately. Give Hook view its own button, for example 37 or a free one at 32 or below (see R6-2).
- **R6-2. Buttons above 32 may be invisible in some games.** Loader Kickdown is 33. Excavator Travel speed is 34, and its Engine speed knob uses 35 and 36. Many DirectInput games read the 32-button DIJOYSTATE, and the Windows Game Controllers panel shows only 32. vJoy also has to be configured with at least 36 buttons, and INSTALL doesn't say that. Renumber machine buttons into the free numbers at or below 32: 10, 18, 20 to 31 aren't all used on every page, so check per page. If that's not possible, document the vJoy button count.
- **R6-3. No round-trip test for actionY.** Add an export/import test (ProfileJson) for a profile with a joystick, asserting that `actionY` and the 2×2 span survive `sanitize`.
- **R6-4. Schema not bumped.** ProfileJson.SCHEMA is still 1. A beta-1 or beta-2 phone importing a profile that contains `"joystick"` gets a raw serialization error instead of "made by a newer version of the app". Bump the schema when a profile uses a joystick, or catch the unknown-enum error and show that message.
- **R6-5 (nit).** Feedback.resolve reads `values[c.id]` for a joystick, which is always null, so its local indicator is always off. PilotJoystick ignores the indicator, so nothing is visible. Either expand there too or leave a comment.

## Still open from round 5
- **R5-1.** ScanBluetooth still runs on the 100 ms tick (CompanionHost.cs:216). Move it to its own task.
- **R5-3.** INSTALL has no note that with several phones, Wi-Fi and Bluetooth links pair by order.
