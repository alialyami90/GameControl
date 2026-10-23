# Reviewer: round 3 findings

Scope: re-verification of every round-2 item in all four components against dev a252ba7, with the round-2 probe tests re-run on scratch copies and new adversarial probes added. Sections are appended as each pass completes. No project files were modified.

Numbering continues. New items: A28-A31.

---

## Android (round 3)

Core suite 23/23 (incl. a21-a24). Probe suite 15: all six round-2 probes now pass; 7 of 9 new probes pass, 2 fail (A28, A29). A27 is still open: `-PcoreOnly=true` still fails on the unguarded Android plugin lines in the root `build.gradle.kts`, the scratch copy had to be patched again.

### Round-2 status
| Item | Status | Note |
|---|---|---|
| A21 | Verified | `syncValue` releases HID holds; shared refcounts behave (two controls on button 8). Side effect: A29. |
| A22 | Verified | Profile setter releases a live HID hold first, then clears `lastValue`/`lastMoved`/`routeOf`. |
| A23 | Verified | `isHeld` skip works; latched `Key(TAP)` after HID reconnect is not re-tapped. Unchanged from A16: a *momentary* tap/press still on screen when its link blips is replayed as a second tap (`AppViewModel.kt:255`). |
| A24 | **Partial** | COMPANION→HID and HID→COMPANION both park correctly, including the race before `onLinkChanged`. Gap: A28. |
| A27 | Not fixed | See above. |
| A16, A18 (FGS), A19, A25, A26 | Open, unchanged | A25: `Inspector.kt:138` still edits the action range independently. |

### MUST (new)
- **A28. Route flip sends position 0 to the companion for a switch/radio.** `InputEngine.kt:145-154` parks every non-release value with `prev != rest`, while PROTOCOL.md:132 limits the park to "momentary or slider". Built-in `gp_mode` (HID-capable positions, AUTO): set to position 2 over the companion, Bluetooth connects, user moves to position 1 → the companion gets `value 0` = a spurious tap of position 0's action (button 17 pulse, or a BeamNG setter), then the HID tap of position 1. Probe `probe3_switchRouteFlipSendsPositionZeroToCompanion` fails. Fix: guard the park with `control.type == MOMENTARY || SLIDER`. (For LATCHING it sends an unlatch to the companion, harmless but outside the spec text.)

### SHOULD (new)
- A29. Game-initiated ON presses a HID button the user never touched. `syncValue` now runs `applyLevel(prev, value)` in both directions (`InputEngine.kt:117-121`); for a latching `VJoyButton` with a `feedbackKey` whose `routeOf` is HID, the game turning the feature on makes the phone press the button, which on a toggle-on-press game binding toggles it straight back off. Probe `probe3_syncOnPressesHidButton` fails. Only releases were needed for A21: restrict the sync-driven `applyLevel` to `value == rest`. The doc comment still says "Nothing is sent".
- A30. A `busy:true` bindings.result locks "Bind in game" permanently: `EditorScreen.kt:628-635` disables the button while `result.busy` and nothing clears `bindResult`. Clear busy after ~2 s or keep the button enabled with the hint.
- A31. `linkDrops` increments on releases and on every slider sample (`AppViewModel.kt:276` has no `isRelease` check), so the NO LINK flash and double haptic fire on a release and loop during a drag. Gate on `!InputEngine.isRelease(c, value)`.

Verified plumbing: `bindResult` parses all eight fields and `message` is shown on `!ok`; `stateKeys` collected from every state update incl. `has.*` and the `visibleWhen` picker filters `has.`; `canBindInGame` live; BeamNG built-in profile 76 unique ids, grid/overlap test passes, `minSpan` enforced in `Profiles.clamp` and `defaultControl`; `EditorBackHandler` composed inside the editor (dirty → confirm, else discard, never saves); `onOpenConnection` wired to both screens; manifest has no `BLUETOOTH_SCAN`, no service declared and none used. PROTOCOL.md lines for the held set, toggle rule and bindings.result match the Kotlin; :132 is the one the code deviates from (A28).

---

## Windows companion and tray (round 3)

Build 0 warnings; project suite 110/110. Round-2 probes: P1, P2, P2b, P6b now pass (the four round-2 failures); 8 new probes, 6 pass, 2 fail (W35, W36). New items: W35-W38.

### Round-2 status
| Item | Status | Note |
|---|---|---|
| W16 | Verified | Emergency release on the throwing thread; locks are re-entrant `Monitor`s, no deadlock path; key-ups survive a throwing router; falls back to `Router.ReleaseAll()` if the engine throws. |
| W17 | **Partial** | Save settings / Forget / LastSeen paths handled. PIN path not: W35. |
| W18-W26, W28, W30, W32-W34 | Verified | W30 caveat stands: with 4096 newer lines queued a `bindings.apply` or `state.request` can be dropped; rare, needs the mod stalled a minute. |
| W27 | Verified | Rebind across device, to xinput, and switch-axis→key all park the old axis (probe logs in the agent report). |
| W29 | Verified (log only) | The phone is told nothing on a handler failure; acceptable. `ConnectionChanged`/`Log` at `BeamNgLink.cs:82-84` are outside the try. |
| W31 | Verified | Momentary setter enters `HeldCommands`, zeroed by the 1 s auto-release, by sync removal and by session end; the 250 ms resend refreshes without re-sending. |
| Toggle rule | Verified | Latching fires on both edges, momentary rising only, switch/radio once per selection; `PROTOCOL.md:110` states it. |

### MUST (new)
- **W35. A failing config save during PIN pairing drops the phone silently and leaves a ghost device.** `Pairing.cs:111-122` `_store.Update` throws out of `Hello` into `HandlePhone`'s outer catch (`CompanionHost.cs:300`): the phone gets neither `welcome` nor `error`, the socket aborts after ~2.6 s, nothing is logged, the PIN is not rotated, and `Config.Devices` already contains the device in memory (`change(Config)` ran before `Save` failed), so a phone that never paired shows in the Phones list. Probe `R3_W17` (config path made a directory) fails. Also `Pairing.Forget` throws before `ForgetDevice` releases and closes the live session (`CompanionHost.cs:665-672`), so a failed Forget leaves the phone connected and injecting while its row disappears. Fix: catch around the PIN `Update`, reply `error` with a `config_write_failed`-style code and log; in Forget, release/close the session first, then persist.

### SHOULD (new)
- W36. A momentary one-shot whose release is lost swallows the next press (pre-existing, found by the new probes). `Tick` (`BindingEngine.cs:153`) resets `LastApplied` only when `st.Holding`; a momentary `*.toggle`, `press`-trigger key or macro holds nothing, so after all three UDP release copies are lost `LastApplied` stays 1 and the next press is not a rising edge. Reset `LastApplied` for momentary controls after 1000 ms of silence regardless of `Holding`.
- W37. A binding edit while the control is held re-fires the new one-shot on the next 250 ms resend (consequence of W28: `SetBindings` nulls `LastApplied`, the resend counts as a rising edge; P3 log `E down | E up | Q down | Q up` with no new press). Low severity; set `LastApplied` to the resend's value for one-shot actions when the shape changes.
- W38. `ConfigStore.Save` retries with `Thread.Sleep` (100 + 200 ms) on the UI thread (`CompanionConfig.cs:100-113`); make the UI callers async or move the retry off-thread.

### U11 livery port
Verified: `Rig.axaml:9-30` tokens equal the DESIGN.md frontmatter; no `DropShadow`/`BoxShadow` anywhere in Companion.App; `Plate.cs` draws fill + pen only; the italic and ExtraBold TTFs exist and their name-table families match every `avares://…#` reference; `Assets\**` embedded. Extras not in DESIGN.md (ElectricHover `#FF4F93`, ElectricPressed `#E01F69`, OnAmber, four Dim colours, a hard-coded `#FFC24D` at `Rig.axaml:229`) should be added to DESIGN.md or derived from tokens so the phone and PC stay one system; `SignalPath.cs:47-51` and `Rows.cs:86-92` duplicate the values instead of reading the resources.
Screenshots (11): no orange/olive remnants, no overlaps. 05/06: node detail "6 controls · BeamNG · …" ellipsised at 214 px. 10 (820×560): signal-path node titles truncate ("Keybo…", "vJoy j…", "Xbox …", "Scancod…"), the page subtitle wraps beside RELEASE ALL INPUT, lower nodes cut by the window bottom (scrollable). Suggest a minimum node width with two-line titles below ~900 px. 07: the "Paired below 960 px" rule is on the content width (`PhonesView.axaml.cs:12-13`), so at a 1120 px window the PAIRED column is already gone; raise the breakpoints or key them on the window.

---

## UI (round 3)

Re-score: **34/40** (was 28/40). Changes: match real world 3→4 (live key picker, device named in words), control/freedom 2→3, consistency 2→3, error prevention 2→3, recognition 3→4, error recovery 2→3. Held at 4: status, aesthetics. Held at 3: flexibility, help. U11 verified in the Windows section above. New items: U28-U33.

### Round-2 status
| Item | Status | Note |
|---|---|---|
| U12 | Verified | Restore dialog with the agreed copy; Undo restores through `onProfileChange { before }`; harmless after leaving the editor. Residuals: U31. |
| U13 | Verified | Rail 56dp, canvas 568dp at 640dp, 62dp cells, 340dp slide-over; the 640×360 screenshot matches the computation to within 1dp. |
| U14 | Verified | `firstFreeCell` null → palette disabled, strip with "Add a row". |
| U15 | Verified | Tabs, SlantButton, resize handle, rail items, KeyField rows all ≥48dp. Remaining: ChoiceChips 40dp (`Form.kt:198`, interactive). Core `minSpan` is now applied in `sanitize`; mismatch: core forces SWITCH/RADIO to n×1 (`Profiles.kt:87`) while the app allows 1×n vertical (`LayoutRules.kt:198`), so a vertical switch saved by the editor is widened on the next load. |
| U16 | Verified (code) | Flash on `linkDrops`, double haptic, both badges, 48dp target to Connect. Gap: no `liveRegion`, so TalkBack hears "Press not sent: no link" only with focus on the badge. Android A31 makes it fire on releases. |
| U17 | Verified | `isError` + supporting text, grouping, NumberPassword, auto-submit at 6, disabled PRIMARY as faint outline (both PIN screenshots). Residual: wrong digits stay in the field with PAIR enabled; red stays while retyping. |
| U18 | **Partial** | Dialog, device in words, per-pair list, persistent row with tones and the "file written, restart BeamNG" case: all present (`editor_bind_result.png`). Not fixed: U28. |
| U19 | Verified | Hollow band + sparse slashes vs solid stripe, distinct in a still frame (`play_small.png` HAZARD vs IGNITION). |
| U20 | Partial in effect | Bottom padding fixes the last card, but `profiles_portrait.png` and `profiles.png` still show the FAB covering a mid-list card's stats/miniature. |
| U21 | Verified | `EditorBackHandler` = X, never saves. `AppViewModel.back()` Editor branch (`:217-219`) is now dead code; remove it. |
| U22 | Partial (as claimed) | Still fixed: ChoiceChips 40, capability chips 32, gear plate 52×40 with `Clip`, Connect title row 64. |
| U23, U24, U25 | Verified | Contrast 4.77/7.13; PC panel first when BT unsupported; KeyField greyed without live keys. |
| U26 | Mostly | Readout merged, BUSY amber, game tag suppressed. "4X4" still clipped in `play_small.png`: U29. |

### MUST (new)
- **U28. "Bind in game" misreads `busy` and previews stale pairs.** In the protocol `busy` is the mod's rejection of a second apply within 1 s; `EditorScreen.kt:628-637` treats it as in-progress ("Binding…", button disabled, row hidden) and nothing ever clears it, so one rejection leaves a dead button (same root as A30). Conversely nothing sets an in-progress state when a request is sent (`AppViewModel.kt:335-350`). And preview/apply read `repo.get(profileId)` before the editor's profile (`AppViewModel.kt:336, 353-356`), so unsaved `bindTo`/button edits show and write stale pairs; with zero pairs the dialog still offers "Bind 0 controls in BeamNG?". Fix: VM sets `bindResult = busy` when sending (≈10 s timeout to a red "No answer from BeamNG"); map the mod's `busy` reply to a red row "BeamNG is still writing the last request. Try again in a second." with the button enabled; read `_editor.value?.profile` first; disable the button with a hint when the pair list is empty.
- **U29. Page tabs clipped on the smallest phones.** `play_small.png` shows "4X4" cut to "4X"; the clip is the `horizontalScroll` viewport of the `weight(1f)` Box (`PlayScreen.kt:209-215`), not the text, so ellipsis cannot help, and tabs are the only page switch mid-drive. In `tight` mode use a prev/next pager (two 48dp buttons with the current name between), or `bringIntoView` on the selected tab with a fade edge and the name column dropped to 96dp.
- **U30. Resize handle swallows the move gesture at phone width.** `EditorScreen.kt:539-558`: a 48×48 handle at BottomEnd inside a 1×1 cap of ~62×49dp covers all of its height and 77 % of its width, so dragging a selected button resizes instead of moving. On narrow layouts hide the handle (the inspector's Width/Height steppers remain) or render it half outside the cap with a hit box of min(48dp, cellW/2, cellH/2).

### SHOULD (new)
- U31. Safety-net scope: the restore dialog has no neutral "Keep it" (`ProfilesScreen.kt:183-188`; the custom-delete dialog has one); Undo restores the whole profile as of the delete, so edits made during the snackbar window are reverted and `pageIndex` is not restored after a last-page undo; `dirty` stays true after undoing back to the original. Label "Undo delete" and re-insert only the deleted control/page via LayoutOps.
- U32. Recovery details for a driver in the dark: `liveRegion = Assertive` on the badge row so NO LINK is spoken (`Hud.kt:85-93`); clear `pin` on a wrong-PIN error and drop `isError` on the first new keystroke (`ConnectScreen.kt:314-322`); ChoiceChips to `heightIn(min = 48.dp)` (`Form.kt:198`).
- U33. Screenshot defects: `editor_small.png` sixth palette item (Knob) cut at the bottom with no scroll affordance; `connect_pin_wide.png`/`connect_done.png` panels run off the bottom so the Disconnect buttons are not in the captures (QUIET verified from code only); the minSpan n×1 vs 1×n mismatch above.

Strengths this round: the narrow editor is a real second layout, not a squeeze; feedback states read in a still frame and in the dark; Bind in game has a before and a durable after.

---

## BeamNG mod (round 3)

Suite 147/147 on 10 runs; zip identical to source. B21 probe: no-ESC car with the nop dummy now omits `has.esc`. New items: B25-B29.

### Round-2 status
| Item | Status | Note |
|---|---|---|
| B18 | Verified | Tokens monotonic, fallback runs once, failing fallback logged; hold switching route mid-hold releases on the new route. Minor: one token per action, so an action with a failing `onDown` and a succeeding `onChange` still triggers the fallback. |
| B19 | Verified | Entries shallow-copied; `hasAction` live. |
| B20 | Verified, residual | Busy reply, pending cleared in `onDisconnected`, `alreadyBound` documented. Residual: B25. |
| B21 | Verified (dummy case) | Blind spot: B27. Per-vehicle log line prints `has.esc=unknown`. |
| B22 | **Partial** | Vehicle-route holds (horn, starter) released in the old car. Action-route holds are not: B26. |
| B23 | Verified | 30 frames at 10 fps = 3 s vs 5 s timeout; break-even 6 fps; test pumps dt = 0. |
| B24 | **Partial** | README is structurally right but names controls that are not in the profile: B28. |
| Toggle rule | Verified | Consistent with the companion (`.toggle` always sent with value 1, never held). Stale wording at PROTOCOL.md:218 ("flips state on the rising edge") contradicts :110. |

### MUST (new)
- **B26. Recover keeps rewinding the old car after a vehicle switch.** `recover` routes through the game's `recover_vehicle` action whenever it exists (`gameControllerLink.lua:481-486`), which is ctx `tlua` and addresses `be:getPlayerVehicle(0)` [likely]; `opts.vehicle` only changes the vlua destination, so `releaseHeld(old)` cannot aim GE code at the old car. Probe: hold Recover in car 7, Ctrl+E to car 8 → car 7 gets only `setActive(false)`, car 8 gets `recovery.stopRecovering()`. Same for any `action.<tlua>` hold. The suite passes only because the mock declares `recover_vehicle` as `ctx="vlua"` (`run.lua:198`), which is not how the game ships it. Fix: in `releaseHeld`, for an `"action"`-route hold whose def has `vehicle=true`, also send `vehicleCommandTo(old, name, "once", 0)` (the vehicle-side handler calls `recovery.stopRecovering`, idempotent); make the mock tlua/player-addressed; add "hold Recover across Ctrl+E" to the smoke test. Also guard `releaseHeld(nil)` so action-route releases are dropped rather than falling through to the new car (`:291`).
- **B28. Smoke test names controls and a profile that do not exist**, so a non-developer cannot run it. README vs `BuiltInProfiles.kt:111-170`: "Gear up/down" are "Shift up/down"; "Slow motion" is "Slow-mo"; "Cruise set/+/-" are "Set" and the "Cruise speed" rotary; "Starter" is "Start"; ESC and the diff/4WD/range controls live on the second page "4x4", never mentioned; "Slower/Faster" exist only in the Bluetooth-pad profile where they are not mod commands. Step 4 (Bind in game) must be run from the "BeamNG.drive (Bluetooth pad)" profile (the only one with `bindTo`), and "output set to vJoy" is unexplained. Cars: the Bastion probably has ESC (pick an Ibishu Pigeon or Gavril Barstow [likely]); the D-Series has a part-time transfer case, so "Center diff" is expected hidden there, say so. Ask the tester to paste the three `vehicle <dir>: has.…` lines unconditionally (that covers B2/B4/B21 in one go), add "hold Recover across Ctrl+E" (B26) and "spawn a new car while holding Horn", and make step 11 about low frame rate (Ctrl+F under 20 fps), not slow motion as such.

### SHOULD (new)
- B25. `closeSocket()` is called directly from `flush()` on a send error (`:86-88`) and from `send()` on overflow (`:95-99`), bypassing `onDisconnected`: held commands are not released and `pendingBindings` survives to the next connection on those paths. Route both through `onDisconnected`.
- B27. `rawget(c, "toggleESCMode")` misses a controller whose API lives in a metatable `__index` (stock controllers are plain module tables [likely], mod controllers may not be). More robust: accept when `type(c.toggleESCMode) == "function"` and a probe key such as `c.__gcProbe` is *not* a function (the dummy answers every key).
- B29. `alreadyBound` is also true when the mod's own `<vidpid>_gamecontroller.json` from a previous apply is loaded, so every re-apply says "(they were already bound in the game)"; PROTOCOL.md:198 attributes it to the user's `.diff`. Say "already bound" without attributing it, or track whether the file was ours.

---

## Round 3 summary and what blocks the build

Round-2 MUST closure: Android A21-A23 closed, A24 partial; Windows W16, W27, W31 closed, W17 partial; BeamNG B21 closed, B22 partial; UI U11-U17, U19, U21, U23-U25 closed, U18 partial. Nothing came back "Not fixed" except A27 (build convenience).

New MUSTs in round 3 (7): **A28** (route flip taps position 0), **W35** (failed config save drops a pairing phone silently), **U28** (Bind in game busy dead-end + stale preview; same root as A30), **U29** (page tabs clipped on small phones), **U30** (resize handle swallows move at phone width), **B26** (Recover keeps rewinding the old car), **B28** (smoke test unrunnable as written). All are narrow and each has a one-paragraph fix above. The trend is right: round 1 had 19, round 2 13, round 3 7, and none of the round-3 items is a new class of bug.

Blocks the APK/companion build for ALI: A28, W35, U28, U29, U30, B26, B28. Everything else can ship as SHOULD.
Sign-off conditions: the seven above fixed with their probes turned into tests (I will re-run my scratch probes, ~30 minutes), plus ALI's in-game smoke test reporting the three `vehicle …` lines and steps 2, 4, 5, 10, 11 passing. Not verifiable by anyone here: real SendInput/vJoy/ViGEm behaviour, Bluetooth HID pairing with Windows, mDNS on a multi-NIC PC.
