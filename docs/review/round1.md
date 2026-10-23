# Reviewer: round 1 findings

Scope reviewed: android/core, android/app non-UI, the existing play-surface UI (PlayScreen, Controls, ControlCap, Hud, theme), windows/, beamng-mod/, protocol, CI. Not reviewed yet: Profiles/Connect/Editor screens (round 2, with a full impeccable critique once they exist).

Evidence: core tests (13) and companion tests (93) were run on scratch copies and pass; extra probe tests confirmed Android MUST 1, 2, 4, 5, 6 and Windows MUST 1, 2. The BeamNG suite (120 checks) is flaky, about 1 run in 5 fails, cause in BeamNG S1. Nothing in the project folder was modified.

Ranking: **MUST** = wrong behaviour in play, a stuck input, or a spec violation. **SHOULD** = robustness or quality, fix before final.

Cross-cutting theme: **stuck inputs**. Six separate findings (A2, A3, W1, W2, W5, B3) leave a key, button, axis or BeamNG level command held after a disconnect, a route change, a vehicle change or a profile re-sync. Please treat "every held input is released on every exit path" as a test-covered invariant in all three components.

---

## Android (core + app logic)

### MUST
- **A1. Latching/switch/radio events are dropped after the game changes state.** `InputEngine.kt:86-88` ignores an event whose value equals its own `lastValue`, but game feedback (`AppViewModel.kt:141`, `:243`) and `onPause`/`releaseAll` only touch the ViewModel's values. Scenario: game reports gearbox D, user taps P (index 0): engine's rest value is 0, so nothing is sent. Scenario: left signal self-cancels in BeamNG, UI syncs to 0, user taps again: value 1 is treated as a repeat and dropped. Fix: feed synced values into the engine (`engine.syncValue(id, v)`), or drop the engine-side check. **Protocol change needed too:** the companion's "apply only when val differs from the last applied value" rule has the same failure (its last applied is 1 while the game is at 0). These events ride WebSocket with sequence numbers, so dedupe by `s` only; remove the value check from PROTOCOL.md and BindingEngine.
- **A2. A held control that becomes unavailable is never released.** `AppViewModel.kt:209` and `:220` return early for any value of an unavailable control, including the release. Hold "Fog" (HID button 8, `visibleWhen has.fog`), switch to a vehicle without fog: button 8 stays down forever (over the companion the 250 ms resend keeps it alive too). Fix: block only presses, always pass releases.
- **A3. Route changes strand held inputs.** `InputEngine.kt:92-98` routes each event by the route that applies now, not the one the press used; `AppViewModel.kt:126-129` reacts to an HID disconnect only by re-sending profile.sync and never clears `HidState`. Hold Gas (W) over Bluetooth, BT drops, release: W stays counted and is sent again on the next report after reconnect. Fix: route falling edges to `routeOf[id]`; on HID disconnect call `hid.releaseAll()` and reset HID-routed values; on HID connect send the current keyboard and gamepad reports.
- **A4. Unipolar axes rest at 50 %.** `Hid.kt:82`, `:122` fill axes with 0, which is the centre of -32767..32767; `InputEngine.kt:204` maps unipolar 0..1 to -1..1, so rest must be -32767. The Generic gamepad throttle reports half input on connect and after every pause/profile switch. Fix: on profile set, connect and releaseAll, initialise unipolar axes to -1.
- **A5. Unknown action type fails the whole import** (`Model.kt:141`, `Protocol.kt:35`), against the spec's "unknown type = none". Fix: `SerializersModule` with `polymorphicDefaultDeserializer(Action::class) { Action.None.serializer() }`.
- **A6. Import sanitiser misses duplicate control ids across pages** (`Profiles.kt:96`, `seen` is per page). The second control then fires the first one's action. Fix: hoist `seen` out of the page loop; dedupe page ids too.
- **A7. Axis range disagreement between HID and companion.** `Protocol.kt:96` takes range from the action only; `InputEngine.kt:204` also honours the control's range. A slider set unipolar on the control but bipolar on the action maps 0..1 to the full axis over HID and to centre..max through the companion. Fix: emit unipolar in `binding()` if either is unipolar, and keep the two in sync in the model.
- **A8. `onServiceDisconnected` never reports the HID link down** (`BluetoothHidLink.kt:88-93`), so the companion keeps skipping `route:"hid"` ids and inputs vanish. Fix: invoke `onConnectionChanged(false)` there.

### SHOULD
- A9. Duplicate WebSocket after reconnect: the 3 s reconnect runnable is never cancelled and `open()` overwrites `ws` without closing it (`CompanionClient.kt:211`, `:109-119`).
- A10. UDP socket race between `openUdp` on the IO executor and `closeSocket` (`CompanionClient.kt:217-239`); check `generation` inside the task.
- A11. `version_mismatch` loops reconnecting every 3 s and the phone never replies/closes when it receives a higher `v` (`CompanionClient.kt:160-181`). Make it fatal with a visible error.
- A12. Discovery: NSD may resolve IPv6 and `connect()` splits on `:`; pre-Android 14 allows one `resolveService` at a time; a stale delayed `stopDiscovery()` can cut a new scan short (`CompanionClient.kt:84-86`, `:291-304`).
- A13. `releaseAll` does not cancel running tap/macro coroutines; a long macro keeps typing after a profile switch (`InputEngine.kt:113-128`, `:242-267`).
- A14. Rapid rotary detents on an HID `vjoy_button` within 80 ms only bump the refcount, so the game sees one press (`InputEngine.kt:216-220`). Queue pulses.
- A15. The resend loop exits on a brief companion drop and does not restart on reconnect while a momentary is still held (`InputEngine.kt:162-167`).
- A16. After a new `welcome` or HID reconnect, latched hold controls and sliders are not re-sent, so the UI shows latched while the PC holds nothing (`AppViewModel.kt:111-116`).
- A17. Keyboard rollover: past 6 keys should report ErrorRollOver (0x01 × 6), not silently drop (`Hid.kt:134`).
- A18. Bluetooth lifecycle: no `ACTION_STATE_CHANGED` receiver (HID not re-registered after BT off/on), `lastHidHost` unused, `onGetReport` returns zeros instead of the current report, `BLUETOOTH_SCAN` requested but not needed, no foreground service so HID can drop when backgrounded.
- A19. Small: tokens keyed by IP (re-pair on DHCP change); liveness counts only pongs; `ProfileRepository.save` does file I/O on the main thread; PROTOCOL.md refers to `protocol/examples/` which does not exist yet.
- A20. Profile content: MudRunner "gear selection" is a single held `R`, and its default keys are unverified against the game; Keyboard profile "Push to talk" on CAPSLOCK hold toggles Caps Lock on Windows (use a dead key like F13 or SCROLLLOCK).

Verified correct: HID descriptor (keyboard 8-byte report id 1; gamepad usage 0x05, 64 buttons, 8 × 16-bit axes, 24 bytes), report encoding, keyboard usage table, `onSetReport` ack, discrete-over-WS / momentary-over-UDP routing, 250 ms resend, 3× release, shared sequence counter reset on welcome, main-thread engine (no races), feedback resolver semantics, built-in BeamNG profile coverage, unique HID buttons ≤ 64, command names match the spec.

---

## Play-surface UI (what exists so far)

Detector (`impeccable detect`) on the ui folder: 0 findings, weak evidence because it scans little Kotlin. No emulator here, so this is a code read. Full dual-agent critique with the remaining screens in round 2.

### MUST
- **U1. Direction drift.** The surface brief `.impeccable/surfaces/android-app-...-ui.md` commits to "Recovery Rig" (graphite + hi-vis orange #FF6A13, yellow #F2C200, chevron latch band), with the decision card noted as still open. The code (`ui/theme/Color.kt`) is "Race Front-End" (blue-black + electric magenta #FF2E7E, slanted plates, italic Barlow Condensed). Either ALI chose Race Front-End, in which case update the brief and write DESIGN.md, or the build drifted from the chosen direction. The designer must confirm with ALI; I cannot tell which was picked. Also `.impeccable/surfaces/windows-src-companion-app.md` points at a folder that does not exist.
- **U2. Momentary button stuck on disposal** (`Controls.kt` MomentaryButton). If the composable leaves composition while held (page switch with a second finger, `visibleWhen` flips, profile reload, activity pause) `onUp` never fires and the key stays down. Add `DisposableEffect { onDispose { if (pressed) onValue(0f) } }`, and have the engine release all on lifecycle pause (ties to A2/A3).
- **U3. HUD overflow on phones** (`PlayScreen.kt` PlayHud). One Row of fixed-width children (name 96-220 dp, page tabs, readouts ~170 dp, two badges ~110 dp each, Edit). At 640×360 dp with three page tabs the badges and the Edit button are pushed off-screen. Needs a compact variant: lamp-only badges, scrollable tabs, or readouts moved to a second strip below 600 dp width.

### SHOULD
- U4. `AxisSlider` launches an animation from inside composition (`if (!dragging && !shown.isRunning && shown.value != value) scope.launch {...}`). Use `LaunchedEffect(value, dragging)`.
- U5. `CapFace` hides the label below 64 dp cap height; on an 8×4 page on a phone 1×1 caps land at ~65 dp, so labels flicker in and out between devices and look-alike icons (diff front/centre/rear) become ambiguous. Lower the threshold to ~48 dp with the small label style.
- U6. `SelectorSwitch` in a 1×1 cell with 3 positions gives ~28 dp segments, under the 48 dp PRODUCT.md promises. Enforce minimum spans per control type in LayoutOps/editor (switch w ≥ positions, slider 2 cells on its long axis, rotary 2×2).
- U7. `GameReadouts` is km/h only; add a units preference (mph) since the target is any Windows game.
- U8. Haptics: `HapticFeedbackType.LongPress` on every momentary down and latch toggle is the heavy buzz; use `Confirm` on API 34+ (fallback `LongPress`) so rapid presses don't feel like a drill.
- U9. The Edit button sits in the play HUD next to the link badges; an accidental tap mid-drive drops the driver into the workshop (principle 4). Long-press, or confirm while a game is connected.
- U10. `RadioKeys` calls `onValue` directly while the other controls go through `rememberUpdatedState`; inconsistent and can hit a stale lambda.

Working well: ControlCap's latch band (solid when confirmed, marching slashes while pending), press travel + wash + border, dashed outline for unavailable (state is never colour-only); the type system (italic condensed display, tabular numerals for readouts, upright body); semantics roles and state descriptions; the rotary's 15° haptic detents.

---

## Windows companion

### MUST
- **W1. Held BeamNG level commands are never released** on phone disconnect, silence or profile re-sync (`BindingEngine.cs:122-134`, `:142-150`, `:228-240`; also `CompanionHost.Dispose`). Only `st.Holds` (keys, vJoy, Xbox) are released. Hold `recover` or `horn`, kill the app: the car keeps rewinding until the user comes back. Probe: horn 1 → Tick(+5 s) → ReleaseAll gives only `[(horn,1)]`. Fix: track held level commands (`BeamNgCommands.IsHeld`), send 0 in ReleaseAllLocked and in the 1000 ms auto-release.
- **W2. Profile re-sync silently releases a latched hold and never re-presses it** (`BindingEngine.cs:44-58`). `SetBindings` calls `ReleaseAllLocked` but keeps `LastApplied`, so the next `val:1` is a Repeat. Latched `vjoy_button` or `key hold` goes up on every edit while the phone shows it latched. Probe: `["vjoy 1 btn 4 down","vjoy 1 btn 4 up"]`. Fix: release only ids whose binding was removed or changed shape, or null `LastApplied` for everything released. (Also see A1: drop the value-based dedupe entirely.)

### SHOULD
- W3. mDNS on multi-adapter PCs (Hyper-V/WSL vEthernet, VPN): sends on the default multicast interface only and advertises every adapter's IPv4 (`Net/Mdns.cs:242-271`). Send per interface and answer with the arriving interface's address, or at least skip adapters without a gateway.
- W4. No idle timeout: a dead phone is not noticed for 30-90 s (`CompanionHost.cs:258-264`). The phone pings every 2 s; release and close after ~8 s silence using `LastMessageAt`.
- W5. Axes are not reset on disconnect (`ReleaseAllLocked` releases buttons only). A throttle left at 100 % keeps the car accelerating. Reset bipolar to centre and unipolar to 0 on session end; spec should say so.
- W6. One exception closes the phone's session (`CompanionHost.cs:263` has no per-message try/catch; `InputRouter.cs:123-132` axis path unguarded; `ViGEmOutput.Pad()` can throw). Wrap and reply `bad_message`.
- W7. Timer callback can crash the process: `Tick` (`CompanionHost.cs:144`, `:163-186`) has no try/catch and no overlap guard at 100 ms.
- W8. Phone keeps stale BeamNG state after the mod disconnects (`CompanionHost.cs:127` clears locally only; `FlushOutGauge :581-596` drops pending changes while the mod is up). Broadcast a `full:true` state on mod disconnect.
- W9. A phone can receive broadcasts before `welcome` (`CompanionHost.cs:371` registers the session before `:379` sends welcome). Register after the send.
- W10. OutGauge lamps never reported if DashLights is 0 (`OutGauge.cs:260`); treat all flags available when `dashLights == 0`, verify in game. `hazard` from both signal bits will blink; debounce on the phone.
- W11. `PushCapabilities` races between timer and BeamNG threads (`CompanionHost.cs:216-225`); lock it.
- W12. Paired-device list read without the store lock (`Pairing.cs:283-284` vs `:316`): two phones pairing at once can throw "Collection was modified"; `Convert.FromHexString` throws on a corrupt hash.
- W13. The companion acquires every free vJoy device 1-16 at startup and every 5 s (`VJoyOutput.cs:141-151`), starving Joystick Gremlin/SimHub. Acquire on first use or only configured devices.
- W14. No WebSocket Origin check (`WsServer.cs:65`): a LAN web page can burn the global 5-per-60 s PIN lockout. Reject upgrades that carry an `Origin` header (OkHttp sends none).
- W15. Small: `BeamNgLink.Send` is a blocking write under the engine lock (use a queue); `OnOutGauge` enumerates NICs per packet; OutGauge port 4444 clashes with SimHub's default, make it configurable and document it.

Verified correct: hello/welcome/error flow, version check first, PIN lockout + constant-time compare + PIN rotation, hashed tokens bound to deviceId, per-session sequence table shared by UDP and WS, UDP key + source IP check, `route:"hid"` skipped but counted, key hold/tap/press, switch/radio semantics, rotary, macros on rising edge, BeamNG edge rules, 1000 ms auto-release, refcounted holds across phones, every scancode in the key table (PAUSE sends E0 45, document it), INPUT struct layout on x64/x86, modifier ordering, vJoy P/Invoke signatures and axis range, ViGEm usage, OutGauge offsets and gear mapping, mDNS records + goodbye packet, loopback-only mod link, CI job.

---

## BeamNG mod

Confidence tags: [certain] / [likely] / [verify] = needs a check in a running game. The test suite stubs mirror the code's own assumptions, so none of M1-M6 can be caught by it; the first in-game smoke test is the real gate.

### MUST
- **B1. Action context defaults to GE Lua** (`gameControllerLink.lua:136`, `:103-118`). Actions without `ctx` are run with `loadstring` in GE; BeamNG's default for a missing `ctx` is `vlua` [verify]. `electrics.toggle_lights()`-style actions then throw in GE, `tapAction` still returns true, and the vehicle fallback never runs: hits every `action.*`, `gear.up/down`, `recover`, `reset`, `slowmo.*` and the ESC/4WD fallbacks. `ui` actions get loadstring'd as Lua. Fix: prefer `core_input_actions.getActiveActions()` [verify name]; default missing `ctx` to `vlua`; skip `ui`; have `runAction` return false on failure; scan `/vehicles/common/` too.
- **B2. 4WD assumes mode names "connected"/"disconnected"** (`gameControllerVehicle.lua:95-96`, `:206`, `:359-363`). Use `mode ~= "disconnected"` for active, restore the last engaged mode for "on", and change modes through `powertrain.setDeviceMode` [likely] so game UI/electrics follow (same for diffs `:350` and range `:371`).
- **B3. Bindings file location and reload are unverified** (`gameControllerLink.lua:447-486`): writes `settings/inputmaps/<vidpid>_gamecontroller.json` and calls `core_input_bindings.onFileChanged(path)`. It is not certain BeamNG loads arbitrary `<vidpid>_*.json` or that calling the hook reloads anything [verify], yet the mod reports `ok:true`. Verify in game; otherwise write `<vidpid>.json` or merge into the user's `<vidpid>.diff` without clobbering it, and confirm the bindings loaded before reporting ok. (vidpid `bead1234`, `button{N-1}`, entry format are [likely] right.)
- **B4. ESC detection may find nothing** (`gameControllerVehicle.lua:110-117`, `:131`): duck-typed on `getCurrentConfigData` + `toggleESCMode`, reads `cfg.escEnabled` [verify]; newer vehicles use `driveModes` [verify]. Use `controller.getControllerSafe('esc')` and `driveModes`; omit `has.esc` when unknown rather than sending false.
- **B5. Cruise control may be silently dead** (`gameControllerVehicle.lua:196-198`, `:289-301`, `:422-440`): `extensions.cruiseControl` may be nil until loaded; electrics keys `cruiseControlActive/Target` [verify]. Load it explicitly and read state from the extension when the keys are missing.
- **B6. Starter uses `electrics.setIgnitionLevel(3)`** (`gameControllerVehicle.lua:407-414`) [verify it cranks]. Prefer `controller.mainController.setStarter(true/false)` with the level as fallback.

### SHOULD
- B7. **TCP_NODELAY never applied** [certain]: `setoption("tcp-nodelay")` is called on the unconnected master socket (`gameControllerLink.lua:556`), returns an error swallowed by pcall. Nagle + delayed ACK adds ~200 ms on Windows and is the cause of the flaky tests (15/15 pass with the fix). Call it in `onConnected()`.
- B8. Retry cycle is ~6 s on Windows, not 3 s (`gameControllerLink.lua:568-580`): a refused non-blocking connect shows in exceptfds, and `select` only watches writefds. Re-call `connect` in `pollConnecting` and treat anything but "timeout"/in-progress as failure.
- B9. Vehicle-route holds (`horn`, `starter`, `recover` fallback) are not in `heldActions` and are not released on disconnect (`gameControllerLink.lua:540-551`, `:376-384`).
- B10. Heartbeat and state polling run on sim time [verify] (`gameControllerVehicle.lua:474-488` vs `VEHICLE_TIMEOUT` 3 s real): in slow motion the GE side reloads the vehicle extension every 3 s. Use real-time deltas or skip the check while paused/slowmo.
- B11. `gearbox.mode` flips the user's gearbox setting from arcade to realistic as a side effect (`gameControllerVehicle.lua:387`).
- B12. Manual gearbox path uses `features.gearbox.setGearIndex` [verify]; `controller.mainController.shiftToGearIndex` is safer. Check `AUTO_GEAR_INDEX` {P=1,R=-1,N=0,D=2,S=3,M=6} [verify].
- B13. `has.fog` / `has.light_bar` are probably always true (`e.fog ~= nil`), so those controls are never hidden; detect from vehicle data or omit when unknown.
- B14. `game.slowmo` is probably true while paused (`gameControllerLink.lua:241-243`, `:317`); check `getPause()` first.
- B15. `require("socket")` at `gameControllerLink.lua:8` is unguarded; pcall with fallbacks and a clear log line.
- B16. GE reaches the vehicle extension via the global name (`:285`, `:298`); use `extensions.gameControllerVehicle`.
- B17. Test harness: its own receive loop drops partial lines (`tests/run.lua` ~:200); add tests for refused connect, split lines, and holds released on disconnect.

Verified correct: full minimum command set in `commands`, toggles on rising edge + idempotent setters, hello/state/full/state.request/bindings.result shapes, `hid` without vidpid → ok:false, hooks (`onUpdate`, `onVehicleSwitched`, `onVehicleSpawned`, `updateGFX`, `onReset`), `queueLuaCommand`/`queueGameEngineLua`, modScript load pattern, non-blocking receive with partial-line buffering and partial-send tails, full snapshot on connect/switch/spawn/request.

---

## Docs and CI
- `docs/INSTALL.md` mentions `windows/README.md`, which does not exist. `README.md` says INSTALL.md is "added with the first release" but it already exists.
- CI: Android job runs core tests, then both APK flavours; fine. Add the BeamNG Lua tests' flakiness fix (B7) before relying on that job.

## What I need for round 2
1. The three remaining screens, then I run the full impeccable critique (dual-agent, with the detector) on the whole UI.
2. A note per MUST on what changed, so I can re-verify instead of re-reading everything.
3. Someone with BeamNG installed to run the in-game smoke test for B1-B6; without it the mod cannot be signed off, only the protocol and code shape.
