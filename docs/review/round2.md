# Reviewer: round 2 findings

Scope: re-verification of the Android round-1 fixes, the full impeccable UI critique (all Android screens + Windows companion window), and a code review of the new Windows tray app. All four round-1 reply files were re-verified; each component has its own section below.

Evidence: core suite 19/19 on a scratch copy (6 new regression tests), companion solution builds with 0 warnings and 93/93 tests pass, 25 screenshots inspected, detector run (no Kotlin/AXAML coverage, see UI section). Probe tests written in scratch copies reproduce every Partial below. No project files were modified.

Numbering continues from round 1. New items: A21-A27, U11-U27, W16-W26.

---

## Android (round 2)

### Round-1 MUST status
| Item | Status | Note |
|---|---|---|
| A1 | **Partial** | Companion path correct (test passes). HID path has two gaps: A21, A22. |
| A2 | Verified | Releases pass on unavailable controls; UI also releases on dispose and spring-back in `finally`. |
| A3 | **Partial** | Route-of-press and `onLinkChanged` are right, but the new `replayHeld` introduces a stuck key: A23. Slider route flip: A24. |
| A4 | Verified | `resetAxesState` on profile set, releaseAll, HID loss; HID connect flushes. |
| A5 | Verified | Default deserializer covers `action`, `positions[].action`, `cw`, `ccw`, and a missing `type`. |
| A6 | Verified | `seen` sets hoisted over the whole profile, pages too. |
| A7 | **Partial** | `effectiveAction` fixed one direction; the editor can still make control=bipolar + action=unipolar: A25. |
| A8 | Verified | Also includes the `ACTION_STATE_CHANGED` receiver and live `onGetReport` from A18. |
| A9, A10, A13, A14, A15, A17, A20 | Verified | A13 note: the existing test does not actually run a macro; the scratch probe does. |
| A11 | **Partial** | Phone-side higher `v` is fatal now, but a `version_mismatch` *error from the companion* still falls in the non-fatal branch and reconnects every 3 s (`CompanionClient.kt:169-171`). Add it to the fatal set. |
| A12 | Partial (as claimed) | Also: `AppViewModel.kt:135` `host.substringBefore(":")` breaks the saved-token lookup for a bracketed IPv6 lastHost. |

### MUST (new)
- **A21. HID refcount is not idempotent against `syncValue`.** A latching `VJoyButton`/`Key(HOLD)` held over HID, the game reports it off, `syncValue(id, 0)`, user taps: `applyLevel(prev=0, 1)` bumps the count 1→2; unlatch drops it to 1 and the button stays down. Probe `probe_momentarySyncThenRepressOverHidRefcount` fails ("button 8 must be up after unlatch, was 128"). Fix: when `syncValue` moves a HID-routed hold to rest, release the underlying key/button (or make HidState level-based per control id rather than refcounted across controls).
- **A22. Stale `lastValue` across profile set.** `InputEngine.profile` setter (`InputEngine.kt:48-53`) never clears `lastValue`/`lastMoved`/`routeOf`; `releaseAll` resets only momentary/slider. Latch a button on profile A, leave play, play A again: UI shows off, first tap sends 1 with previous=1 and is a no-op. Probe `probe_staleLatchValueAcrossProfileSet` fails. Fix: clear the three maps in the setter.
- **A23. New regression: `replayHeld` double-presses HID holds.** `AppViewModel.kt:223-244` runs on every `onWelcome` and HID connect; for a control already held over HID (both links up, AUTO) it does `syncValue(id, rest)` + `onValue(c, v)` → routes to HID again → `hid.keyDown` count 2 → the user's release leaves 1 → **key stuck until releaseAll**. Hold W over Bluetooth, the companion (re)connects, let go: W stays down. Probe `probe_replayHeldWhileHidHoldsDoubleCountsKey` fails. Fix: replay only ids the engine forgot (expose `engine.isHeld(id)` and skip live ones).
- **A24. AUTO route flip mid-drag leaves the companion axis set.** Slider at 0.6 over the companion, HID connects, later moves and release go to HID; the companion is never told, and per PROTOCOL.md:147 sliders keep their last value, so the vJoy axis stays at 60 %. Probe `probe_sliderRouteFlipLeavesCompanionValue` fails. Fix: when a non-release value changes route, send rest on the old route first.

### SHOULD (new)
- A25. Keep control range and action range in sync in the model (`Inspector.kt:138`, `ActionEditor.kt:170` edit them independently); with control=bipolar + action=unipolar the lower half of the slider is dead on both routes. Simplest: derive the action's range from the control for sliders and hide the action-level field.
- A26. User-initiated `disconnect()` (`CompanionClient.kt:103`) never calls `onDisconnected`, so `onLinkChanged` is not invoked and the resend loop spins on dropped sends.
- A27. `-PcoreOnly=true` still needs Google Maven because root `build.gradle.kts:3` declares the Android plugins `apply false`. Guard those with the same property if core-only local/CI runs are intended.
- Still open from round 1 by agreement: A16 (re-send latched holds after welcome/HID reconnect, now partly covered by replayHeld once A23 is fixed; note `onPause → releaseAll` drops HID latched holds while the UI keeps them), A18 remainder (foreground service, drop `BLUETOOTH_SCAN`), A19.

### Protocol text vs code
- PROTOCOL.md:107: the case control=bipolar + action=unipolar is undefined (A25).
- PROTOCOL.md:147 "sliders keep their last value" is what makes A24 stick; add "when a control's route changes, the phone sends rest on the previous route".
- :5/:54: the phone now replies and closes on a higher `v`; the companion-sent `version_mismatch` handling is A11.

---
## UI critique (impeccable, dual-agent)

Method: dual-agent (A: design review from source + all 25 screenshots · B: detector + static a11y/geometry + screenshot facts), synthesised here. Limits: no emulator or browser in this environment, so no overlay pass and no runtime measurement; the detector (`impeccable detect`) parsed zero Kotlin/AXAML files for both targets (verified with a control HTML file), so its "clean" result is no coverage, not a pass. The UI designer's round-1 replies landed while the assessments ran; items they fixed (U2-U10, confirmed in code and in `play_small.png`) are not repeated below.

### Design health (Android app, Operate)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 4 | Lamps, pending stripe, UNSAVED, latency: strong. "Bind in game" ends in a snackbar with "…" and no completion state. |
| 2 | Match system / real world | 3 | Tell-tale icons and "Lights up in" are excellent; Game link asks for raw keys (`has.diff.front`) with no vocabulary surfaced. |
| 3 | User control and freedom | 2 | Built-in "Restore original layout" fires with no confirm; control and page delete have no confirm or undo; hardware Back saves silently while X asks to discard. |
| 4 | Consistency and standards | 2 | Phone and PC apps are on two different design systems; Back saves vs X discards. |
| 5 | Error prevention | 2 | Adding to a full page stacks at (0,0); grid steppers allow sub-48dp cells with no warning; PIN field accepts 4 digits though the PC shows 6. |
| 6 | Recognition rather than recall | 3 | Layout miniatures and the icon picker are good; state keys are never suggested. |
| 7 | Flexibility and efficiency | 3 | Duplicate, import/export, output route, page tabs; no key-capture for Key actions, no recently-used icons. |
| 8 | Aesthetic and minimalist design | 4 | Every pixel of colour means something; the HUD earns its strip. |
| 9 | Error recovery | 2 | Wrong PIN shown away from the field; disabled PAIR reads as pressed; dropped presses with no link only produce a throttled snackbar. |
| 10 | Help and documentation | 3 | Connect steps and Outputs blurbs help; nothing explains what a profile is or that BeamNG needs a mod. |
| **Total** | | **28/40** | Good, with a clear list to reach 32+. |

### Design specificity verdict
The play surface is authored for this product and brief: cut-corner caps, accent wash + racing stripe on lit controls, ISO-style tell-tales, italic tabular numerals, slanted gear plate, colour only where it means something. `play_small.png` (640×360) proves the compact HUD now degrades by design. It is a race front end, not a neon dashboard. The workshop screens are coherent Material 3 wearing the same chrome, which is the right call. **The Windows companion is still on the rejected "Recovery Rig" system** (`Styles/Rig.axaml:1-30`: olive-graphite grounds, hi-vis orange, rounded rubber caps with drop shadows; all 10 screenshots), while its own surface brief now says it inherits Race Front-End. Its IA (signal path, PIN as hero, legend with shape + colour) is good and should be kept; only the livery is wrong.

### What's working
- The cap + stripe grammar (`ControlCap.kt`): lit = wash + border + top stripe, pending = marching slashes, unavailable = dashed outline + alpha + "Not available on this vehicle" in semantics. Glanceable without colour, placed where peripheral vision catches it.
- The HUD's compact and tight modes (`PlayScreen.kt:170-210`): lamp-only badges, rpm dropped, name capped, tabs scroll, gear stays loudest. Exactly the "driver glances" brief.
- Profile cards as car-select with to-scale miniatures in each control's accent (`ProfilesScreen.kt`).
- Contrast is sound: every text-on-surface pair in DESIGN.md that is actually used passes 4.5:1 (InkMuted/Panel 7.7, InkFaint/Ground 5.3, OnElectric/Electric 5.6, Ink/Cap 14.3) except two small labels listed below.

### MUST (UI)
- **U11. Port the Windows companion to Race Front-End.** `windows/src/Companion.App/Styles/Rig.axaml`, `Controls/SignalPath.cs`. Tokens from DESIGN.md (ground #0A0D14, panel #111622, cap #1C2333/#262F44, ink #F2F4F8, electric #FF2E7E, green confirmed, amber waiting, red failure); slanted plates for tabs/buttons, cut-corner caps for panels, no drop shadows ("Lit Not Lifted"), italic Barlow Condensed for titles and numerals (the italic/extrabold TTFs are missing from `Assets/Fonts/`). Keep the signal path, PIN hero and legend. Fix the stale line-1 comment.
- **U12. Destructive actions without a safety net.** `ProfilesScreen.kt:159` built-in restore skips the dialog (custom delete has one with good copy); `Inspector.kt:91` trash deletes a control immediately; `EditorScreen.kt:415` deletes a page. Route restore through the AlertDialog ("Restore the original BeamNG.drive layout? Your changes on this phone will be lost. Duplicate it first to keep them."), make control/page delete undoable via snackbar UNDO (the editor already has a dirty model) or confirm when the page has controls.
- **U13. Editor is unusable at phone width.** `EditorScreen.kt:204`: palette 104dp + inspector 340dp fixed; at 640dp the canvas is 180dp, so an 8-column page gets ~14dp cells (B computed; `editor.png` is 914dp wide, which is why it looks fine). Below ~840dp make the inspector a modal bottom sheet or slide-over and collapse the palette to a 56dp icon rail.
- **U14. Full page add overlaps silently.** `EditorScreen.kt:126-132` returns (0,0) when no cell is free. Disable palette items with "Page is full: add a page or enlarge the grid", or grow rows by one with a snackbar.
- **U15. Sub-48dp targets that remain** (B measured from source): `Slant.kt:95-105` SlantTabs are 36dp tall and are the only way to change page while driving (give them a 48dp+ hit box inside the 56dp strip); `Slant.kt:57-74` SlantButton puts `.clickable` on the inner 40dp Row, not the 48dp outer Box; `EditorScreen.kt:582-589` resize handle is 28dp after padding; built-in `gp_mode` selector segments are 46dp and any 1×1 switch/radio is ≤30dp until core adopts `minSpan` (the designer's U6 note: core `Profiles.defaultControl` and the built-in 1×1 rotaries still need the rule).

### SHOULD (UI)
- U16. No-link feedback in play (`AppViewModel.kt:266-272`, `Hud.kt:74-95`): a dropped press only raises a 10 s-throttled snackbar. Flash the relevant badge red with "NO LINK" for ~600 ms, distinct double haptic, make both HUD badges tappable to open Connect.
- U17. PIN entry (`ConnectScreen.kt:242, 263-272`, `connect_pin.png`): show the error as the field's `isError` + supporting text, group digits "482 913" in the same readout type the PC uses, enable PAIR at 6 digits and auto-submit, render disabled PRIMARY as faint outline rather than 40 % alpha magenta (`Slant.kt:58`), which reads as pressed.
- U18. Bind in game has no before/after (`EditorScreen.kt:438-443`, `AppViewModel.kt:306-319`): confirm sheet listing "N controls → BeamNG actions" and the device (hid vs vJoy 1), then a persistent result row ("Bound 12 of 12 · 15:56" or the mod's `ok:false` message). Round-1 B3 now makes the mod report `ok:false, written:true`; the phone must show that.
- U19. PENDING vs confirmed differs only by motion (`ControlCap.kt:115-134`); B confirms under animator scale 0 it freezes to a 22 % band with slashes, which is distinct but faint. Draw pending at half height or hollow so the static frame is unmistakable at arm's length.
- U20. Profiles FAB overlaps the last card's stats (`ProfilesScreen.kt:151-157`, `profiles.png`, `profiles_portrait.png`): bottom content padding ~120dp or a trailing spacer item.
- U21. Editor exit semantics: hardware Back saves silently (`AppViewModel.kt:196`), X asks to discard (`EditorScreen.kt:175`). Pick one (recommend Back = X, with DONE as the only save).
- U22. Font scale: HUD is a fixed 56dp (`PlayScreen.kt:140`) with 26sp gear + 20sp readouts + 11sp units; `heightIn(min = 56.dp)` and let the grid take the rest. Same for StateTag 22dp, LinkBadge 32dp, chips 32/40dp, editor top bar 60dp (B lists them).
- U23. Contrast misses: `ConnectScreen.kt:539` host IP (InkFaint on Cap 4.30:1), `Inspector.kt:110` "NONE" (4.30:1), `ConnectScreen.kt:541` CONNECT (Electric on Cap 4.44:1). Lift to InkMuted or move onto Panel.
- U24. Connect screen on a Bluetooth-unsupported phone still gives the dead Bluetooth panel top billing (`connect_pin.png`); collapse it to one line and put the PC panel first. `connect_done.png` leaves ~55 % of the left panel empty; both Disconnect buttons are OUTLINE electric when QUIET would do.
- U25. Game link fields (`Inspector.kt:169-175`) are free text for `feedbackKey`/`visibleWhen`/`positionKey`; the companion already relays the key set. Offer a picker fed by live state (greyed when the game is off) with free text as fallback.
- U26. Hud.kt:150-156 `Readout` has no `mergeDescendants` (TalkBack reads "71" and "KM/H" separately); `Hud.kt:46-51` uses Yellow for BUSY while DESIGN.md and the PC use amber; `Slant.kt:108-113` tab labels have `maxLines = 1` with no overflow (seen: "4X" clipped in `play_small.png`); `play_builtin.png` shows "BeamNG.drive" and tag "BEAMNG.DRIVE" twice in the HUD; all ~347 UI strings are hard-coded with 0 `R.string` use (fine for v1, blocks localisation).
- U27. Windows window sizing: `MainWindow.axaml:10` min 940×620 overflows a 1366×768 laptop at 150 %; `10-status-min-size.png` already truncates "ViGEmBus mis…" and cuts the Telemetry card. Lower minimums (~820×560) and clamp to the working area on first show (also in the tray code review below).

### Three questions for the designer
1. If the first screen is a car-select, why isn't connection the first car? What if Profiles refused to look "ready" until a link lamp was green?
2. Should a BeamNG profile ever show a free-text field for a state key, given the companion knows every key the mod reports?
3. The gear plate is permanently electric, the active tab is electric, and users may light toggles electric. Which of the three should the driver read first?

---

## Windows tray app (Companion.App, new code)

Build: solution builds in Release with 0 warnings; 93/93 tests pass (Core only; no UI tests). The CI publish step finds `Companion.App.csproj`, self-contained single-file, no trimming/AOT, fonts and icons embedded: verified.

### MUST
- **W16. An unhandled UI-thread exception kills the process without releasing held input.** `Program.cs:9-15`, `App.axaml.cs:62`: no `AppDomain.UnhandledException` / `Dispatcher.UIThread.UnhandledException` handler and no try/finally around the lifetime; `desktop.Exit` does not fire on a crash, so `_host.Dispose()` / `Router.ReleaseAll()` never run and any key or vJoy button a phone was holding stays down in Windows. Fix: emergency release in an `AppDomain.UnhandledException` handler plus try/finally; log and continue on dispatcher exceptions.
- **W17. Config save can throw on the UI thread.** `MainViewModel.cs:380` (Save settings) and `:328` (Forget) → `ConfigStore.Save` (`Companion.Core/Config/CompanionConfig.cs:86-95`) has no try/catch; a locked `%APPDATA%\GameController\companion.json` (AV scan, sync client) throws → W16 → stuck keys. Wrap and surface via `SettingsNote`/log.

### SHOULD
- W18. Addresses under the PIN go stale (`MainViewModel.cs:128-131`): read only in `RefreshPin()`; a PC that starts before Wi-Fi is up shows "No network connection" forever. Re-read in `SlowTick` or on `NetworkChange.NetworkAddressChanged`.
- W19. `netsh` runs on the UI thread (`App.axaml.cs:61` marshals back to UI before `CheckFirewall`; `WindowsSystem.cs:29-34` blocks up to 5 s). The window freezes at first paint. `await Task.Run(...)`.
- W20. No coalescing of axis activity (`MainViewModel.cs:101-103, 276-290`): every slider update is a dispatcher post, an `Insert(0)` into a non-virtualised 60-row list and a redraw; two sliders at 60 Hz = 120 relayouts/s with an unbounded queue. Update the top row for the same (phone, control) or throttle axis rows to ~10 Hz.
- W21. Second instance exits silently (`Program.cs:11-12`); signal the first instance to `ShowWindow()`.
- W22. Window too large at 150 % DPI on 1366×768 (`MainWindow.axaml:10`: min 940×620 → 1410×930 px). Lower minimums (~820×560) and clamp to the working area on first show (also U27).
- W23. OS shutdown/logoff treated as close-to-tray (`App.axaml.cs:39-45`); let `WindowCloseReason.OSShutdown` through so inputs are released cleanly.
- W24. Host event handlers never unsubscribed in `MainViewModel.Dispose()`; unsubscribe before `_host.Dispose()`.
- W25. Firewall check is name-only (`WindowsSystem.cs:25-37`); if the exe moves the rule still "exists" and phones are blocked. Compare the `Program:` line to `ExePath`.
- W26. Minor: `UpdateTray` allocates a new Bitmap/WindowIcon per change and never disposes; ViGEm row stays "Plugs in on first use" after the pad connects; "Last seen" only refreshes on `SessionsChanged`.

Verified correct: all host events marshalled through `Ui()`/Dispatcher, VM built before `Start()`, tray lifecycle (OnExplicitShutdown, hide on close, Quit → Exit → dispose host → release all), single-instance mutex, Start-with-Windows registry round-trip, PIN always 6 digits and shown large in three places, capability rows from `Host.Capabilities()` with 5 s re-probe, bounded log (400) and activity (60), no `DllNotFoundException` path on a PC without vJoy/ViGEm, PerMonitorV2 DPI, font family names match the AXAML references.


---

## BeamNG mod (round 2)

Offline suite 137/137 on 10 consecutive runs (flakiness gone). `dist/gamecontroller.zip` is identical to the source tree and newer than the last edit. Confidence tags as before; the in-game smoke test stays the gate for B1-B6.

### Round-1 status
| Item | Status | Note |
|---|---|---|
| B1 | **Partial** | Hedge works for the nil-global case and `ui`/`ts`/`slua` are skipped. Gaps: B18, B19. |
| B2 | Verified (offline) | `mode ~= "disconnected"`, engaged mode recorded and restored, `powertrain.setDeviceMode` first. |
| B3 | **Partial** | Confirmation reads `core_input_bindings.bindings` sensibly and `ok:false, written:true` flows to the phone's message. Gaps: B20. |
| B4 | **Partial, likely regression** | B21. |
| B5, B6, B7, B8, B11-B17 | Verified | B13 and B5 API names remain [verify]. |
| B9 | **Partial** | Released on disconnect, but not on vehicle switch: B22. |
| B10 | Verified, thin margin | B23. |

### MUST (new)
- **B21. `getControllerSafe('esc')` dummy passes the `toggleESCMode` check** (`gameControllerVehicle.lua:118-140`). BeamNG's safe dummy [likely] answers every key with a nop via `__index`, so `type(c.toggleESCMode) == "function"` is true on a car with no ESC. Probe with such a dummy gives `has.esc = true, has.tcs = true, esc.active = nil`: the opposite of the "omit when unknown" rule, and the toggle calls a nop. Fix: `rawget(c, "toggleESCMode")` or drop the `getControllerSafe` candidate. The suite does not mock `getControllerSafe`, so add that mock. Smoke step 5 cannot catch this (it only checks the control is visible); ask for the `has.esc` value in the companion log on a car without ESC too.
- **B22. Holds are not released on vehicle switch** (`gameControllerLink.lua:754-762` only sends `setActive(false)`). Hold horn/starter/recover on car A, Ctrl+E to car B, release: car B gets the 0, car A keeps horn on, starter engaged or recovery running. Release all `heldActions` against the old vehicle in `onVehicleSwitched` before switching.

### SHOULD (new)
- B18. `runCode` returns true for anything queued to vehicle Lua (`gameControllerLink.lua:193, 206`), so a failure inside the vehicle VM (explicit `vlua` actions and every hedged action) is invisible and the `vehicle=true` fallback in `oneShots` never runs; a GE action with stale code (e.g. `toggle_slow_motion` after an API rename) is re-queued to vlua, errors there, and `slowmo`'s `simTimeAuthority` fallback is skipped. Have the vehicle side report the pcall result back (`obj:queueGameEngineLua`) or, for commands that have a native fallback, prefer the native call and use the action as the fallback.
- B19. `loadActions` writes `_ctx`/`_ctxExplicit` into the game's live `activeActions` entries (`:169-170`); copy instead. `hasAction` (`:213`) is dead code.
- B20. A second `bindings.apply` within the 1 s window overwrites `pendingBindings` (`:548`) so the first never gets a result; `pendingBindings` survives a disconnect and the stale result is sent on the next connection. Queue or reject with `ok:false "busy"`; clear on disconnect. Document `written`/`count`/`unknown`/`device` in PROTOCOL.md (only `ok`/`message` are specified). Note the confirmation returns ok:true if the same pairs are already live from the user's own `.diff`; acceptable, but say so in the message ("already bound").
- B23. At 20 fps in slow motion the frame heartbeat (60 frames) is exactly `VEHICLE_TIMEOUT` (3 s). Use `HEARTBEAT_FRAMES = 30` or `VEHICLE_TIMEOUT = 5`, and add a test that pumps `updateGFX` with dt = 0.
- B24. README smoke test, for a non-developer: step 2 should name the exact phone controls and say the info line "action failed in game engine Lua, running it in vehicle Lua" is normal; step 4 should quote the ok:false message text and say Options > Controls must be reopened; step 5 should include a car without ESC; step 7 cannot distinguish `setStarter` from the fallback (add a log line); say where `beamng.log` is; add "switch vehicle while holding horn" (B22) and a slow-motion soak (B23).


---

## Windows companion (round 2)

Build 0 warnings; 104/104 tests pass. 12 scratch probes: 8 pass, 4 fail (P1, P2, P2b, P6b; mapped below). The reply's note that PROTOCOL.md says 6 s is stale: the spec already says 8 s, no change needed.

### Round-1 status
| Item | Status | Note |
|---|---|---|
| W1 | Verified | Held commands zeroed on every exit path; Dispose flushes the mod channel first (probe: mod receives `horn 0` before EOF). |
| W2 | Verified | `SameShape` covers route, positions, cw/ccw; latched holds survive a re-sync of other ids. Blind spot: W28. |
| A1 (companion) | Verified | Seq-only dedupe; phone never resends discrete events, so no double fire. |
| W3, W4, W7-W14 | Verified | W13/W3 by code read (Windows-only). |
| W5 | **Partial** | W27. |
| W6 | **Partial** | W29. |
| W15 | Verified, caveat | W30. |

### MUST (new)
- **W27. A slider rebound to a different axis leaves the old axis set.** `BindingEngine.SetBindings`: `st.Axis` is overwritten by the next event, so after `vjoy X 1` → re-sync to axis Z, X stays at 100 % forever and session end parks only Z (probe P1 log: `axis X 1 | axis Z 0.3 | axis Z 0`). Fix: park the old axis when a changed binding's axis differs, or `parkAxis:true` for changed/removed ids (the "re-sync does not park" intent only needs to hold for unchanged ids).
- **W31. A momentary control bound to a setter is never zeroed.** Flash-to-pass (momentary `lights.high`) or hazard-while-held: neither the 1000 ms auto-release nor session end sends 0 (probes P2/P2b: `[(lights.high,1)]` only). The hole is kind-based: treat a setter driven by a *momentary* control as held (add to `HeldCommands` when Kind == Momentary), keep latching setters unzeroed as the reply argues, and write both rules into PROTOCOL.md:58/112, naming the held set explicitly (`starter`, `horn`, `recover`, `action.*`, plus momentary-driven setters).

### SHOULD (new)
- W28. `Bindings.ToString()` omits `Range` for `VJoyAxis`/`XInputAxis` (and prints only the step count for macros), so a unipolar→bipolar range edit is "unchanged" and session end parks the axis at 0 instead of centre. Include Range (compare specs field-wise).
- W29. `BeamNgLink.Serve` catches only `JsonException` around `HandleLine` (`BeamNgLink.cs:94-95`); a throwing `StateReceived`/`GameState`/`Logged` subscriber tears the mod link down (the CLI's synchronous `Console.WriteLine` is exposed; the tray posts to the dispatcher). `catch (Exception)` and log. Also `bad_message` is now sent for any handler failure (`CompanionHost.cs:311`) while PROTOCOL.md:7 reserves it for malformed input; use a new `internal_error` code or log only.
- W30. `Channel.CreateBounded(1024, DropWrite)` drops the *newest* message when full, which may be a `horn 0` release behind 1024 stale commands. Use `DropOldest`, or never drop a value-0 for a held command.
- W32. `DropIdleSessions` uses `DateTimeOffset.Now`; an NTP jump > 8 s drops every session once. Use `Stopwatch`/`Environment.TickCount64`.
- W33. `CenterAxes` on vJoy acquisition puts every axis at 0.5, so a unipolar throttle reads 50 % until its slider first moves (same class as Android A4); park by the bound action's range at acquisition. A device freed by another feeder is only usable after the next 5 s refresh.
- W34. A held command fired from a switch/rotary is released by `Task.Delay(80).ContinueWith`; a Dispose inside that window loses the 0. Negligible, noting for completeness.

### On the two "choices to note"
1. Setters not zeroed on disconnect: right for latching, wrong for momentary (W31). Spec must say which.
2. Latching + `*.toggle` fires only on "on": matches PROTOCOL.md:110-111, but key `tap` taps on both latching edges (:104/:123) and to the game a toggle key and a `.toggle` command are the same thing; a user who binds latching→toggle gets a lying UI. Recommendation: "toggle fires on every latching event, rising edge only for momentary" (one line in the engine), and the editor steers latching controls to setters. If the Lead keeps the current rule, make :110 a hard rule and have the editor refuse the combination.

---

## What I need for round 3
1. Android: A21-A24 fixed with the four scratch probes turned into real tests (names above; the probe file is `core/src/test/kotlin/com/gamecontroller/ReviewProbeTest.kt` in my scratch copy, I can paste it into the Lead developer thread if useful).
2. UI: U11 (Windows livery), U12-U15, then the SHOULDs you choose; a fresh set of screenshots including the Windows window after the port, the editor at 640×360, and the Connect screen with a PIN error.
3. Windows: W27, W31 (and the spec lines they need), W16-W17 in the tray app, then W28-W30.
4. ALI: the in-game BeamNG smoke test (`beamng-mod/README.md`, "In-game smoke test") once the Lead developer says the build is ready; it is the only way to close B1-B6.
