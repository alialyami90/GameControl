# Round 4 review (dev f05272e, CI run 39849230721 green)

**Verdict: no code blockers. Ready for beta.** Every round-3 MUST (A28, W35, U28, U29, U30, B26, B28) is verified closed. The only work that must happen before ALI tests is the docs fixes D1 to D10 below. ALI follows INSTALL.md and SMOKE_TEST.md, so those docs ship in the package.

Test results this round: Android core 25/25 plus probes 14/15 (the one failure is a stale probe, see A-S3). Windows 140/140 including ported probes. BeamNG mod 150/150 on 5 runs. The mod zip is identical to source. UI heuristic score is 37/40, up from 34.

Not verifiable here: real SendInput, vJoy and ViGEm output, Bluetooth HID pairing, mDNS on a multi-NIC PC, and BeamNG in game. Final sign-off needs ALI's smoke test.

## Fix in the beta package (docs)

- **D1. No download location.** Both docs start at "copy GameCtl.apk". Add a "Get the files" step. Best is a GitHub release with the three files attached, because CI artifacts need a login and come zipped.
- **D2. Wrong exe name.** INSTALL and SMOKE 0.2 say `GameCtl.Companion.exe`. The built file is `GameControllerCompanion.exe` (Companion.App.csproj:8). The window title reads "GAME CONTROLLER Companion for Windows".
- **D3. No "Enable" button in Bluetooth.** The button is MAKE DISCOVERABLE (ConnectScreen.kt:229-237). On an unsupported phone there is a grey line instead and the PC panel comes first. Fix INSTALL 2a.1 and SMOKE A1. Call the connection chip "the PAIRED / OFF badges at top right".
- **D4. Bind in game path.** It is Edit, then the gear icon on a phone (or the right panel on a tablet), then scroll to BEAMNG. Fix INSTALL 3.4 and SMOKE C4.
- **D5. Companion status wording.** The window shows the node "BeamNG mod / Mod 1.0.0 connected", not "BeamNG: connected". Fix INSTALL 3.2 and SMOKE C1.
- **D6. Mods folder.** Use `%LOCALAPPDATA%\BeamNG.drive\<version>\mods` and "Options > Other > Open user folder", matching beamng-mod/README.md:15-16.
- **D7. Prerequisites.** State Windows 10/11 64-bit. Say .NET is not needed because the exe is self-contained. Cover the SmartScreen "More info > Run anyway" step and the firewall prompt (network set to Private). Say the APK is debug-signed, so Play Protect will warn.
- **D8. Smoke-test labels that don't exist.** A3 "T13" is labelled T1 on screen (button 13). A3 "Mode switch" is "the 1 / 2 / 3 switch". E3 is "Sends > Key or key combo". E5 is "Export to file", and Import is the download icon at top right. E6 is "Restore original layout", confirmed with "Restore".
- **D9. Order.** Install vJoy/ViGEm before starting the companion, or add "restart the companion". INSTALL 3.4 should say Bind in game on the Bluetooth pad profile needs the companion to know the pad, as SMOKE C4 does.
- **D10. Bluetooth pairing notes.** Add one sentence on Windows asking to confirm a code on the phone. Add one on what to do if "GameCtl Pad" never appears (discoverable mode times out after about 2 minutes).

## SHOULD, next cycle

### Android
- **A-S1. Latch state wrong after switching to HID.** On HID connect, a latch held on the companion gets released by the companion's profile.sync. It is never pressed over HID, because replayHeld skips it using the stale COMPANION routeOf (AppViewModel.kt:259). The cap shows ON while the PC has it off.
- **A-S2.** The rejected bind row (EditorScreen.kt:706) shows a hard-coded sentence instead of result.message. The VM (AppViewModel.kt:143) also raises a snackbar for the same rejection, which duplicates the row.
- **A-S3.** The probe `probe3_latchRouteFlipSendsZeroToCompanion` is stale. It asserts the latch park the spec removed. Delete or invert it.

### Windows
- **W-S1. Press swallowed after rebind plus lost release.** Tick doesn't clear `Rebound`. After a rebind while held followed by a lost release, the next press is swallowed once. Fix: `st.Rebound = false;` in Tick next to the LastApplied reset. The probe `R4_Rebound_then_lost_release` covers it.

### BeamNG
- **B-S1.** A held `action.<tlua>` that addresses the player car still releases in the new car after a vehicle switch. It is documented. Leave it as a known limit unless there is a cheap fix.
- **B-S2.** B29 docs say "nothing was written", but the file is written with written=true. Correct the wording.
- **B-S3.** README step 4 quotes the mod's message ("Bound N actions to vJoy device 1"), not the phone's row ("Bound N of N · HH:MM"). Match SMOKE C4, which is right.

### UI
- **U34. First-time PIN field opens in error red.** `CompanionClient.open()` (net/CompanionClient.kt:113) enters PAIRING with message "Enter the PIN shown on your PC.". That trips the wrong-PIN effect (ConnectScreen.kt:316-329), so the empty field shows red before anything is typed. The previews use a hand-built state and hide it. Fix: leave `message = null` in open(), because summary() already carries the prompt (Hud.kt:60). **Recommended for the beta build**, because every tester's first pairing will hit it. It's a one-line fix.
- U35. `bindDeviceName()` (EditorScreen.kt:636-640) prints "vJoy device  device 1" for input like "vjoy device 1". Make it tolerant, or fix the preview data.
- U36. The palette scroll fade (EditorScreen.kt:402-416) is barely visible on the near-black ground. Add a chevron or a lighter fade.
- U37. `canBindInGame` is frozen at edit() time (AppViewModel.kt:437). A companion drop while editing leaves the button enabled until a snackbar fires.
- U20 (carried over). The NEW PROFILE FAB still covers a mid-list card's stats row.
- Minor: phone-wide editor truncates cap labels ("IGNIT…"). The resize handle is a sub-48dp drag target at 32-47dp, by design. Undo appends the control rather than restoring list position, which has no visible effect.

## Verified this round
- **Android:** A27 (core-only build runs unpatched), A28, A29, A30/U28 VM side, and A31.
- **Windows:** W35 (rollback, SaveFailed, `config_write_failed`, no ghost device, PIN not rotated, Forget releases before persisting), W36, W37 (with W-S1 residual), W38, and W29 guards.
- **BeamNG:** B21, B22, B25, B26, B27, B29, and the PROTOCOL.md:218 wording.
- **UI:** U28 (busy, timeout, mod-busy retry, editor-profile-first, empty-pair hint), U29 pager, U30 handle, U31 to U33, the minSpan agreement between core and app, the back() branch, and the ChoiceChips at 48dp.
