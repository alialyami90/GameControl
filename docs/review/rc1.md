# 1.0 release candidate 1 review (dev 7d52dd3, CI run 39889788025 green)

**Verdict: not ready. 26 comments are open: 3 MUST and 23 minor.** Tests pass: Android core 31/31, Windows 127/127, BeamNG mod 172/172 on 5 runs. The mod zip is identical to source. The docs pass (rc1-docs 1-12) is fully closed.

## MUST (open)
- **M1. Companion holds are never forgotten, so A16 and A26 are still open.** `CompanionClient.disconnect()` (CompanionClient.kt:108) and `lost()` (:221) call `listener?.onDisconnected()` while the state is still CONNECTED. `engine.onLinkChanged()` then reads `isConnected == true` and never runs `forgetHeld(Route.COMPANION)` (InputEngine.kt:196). The next welcome's replayHeld skips the latch (AppViewModel.kt:270), so the cap shows ON while the PC holds nothing. Fix: update the state to DISCONNECTED or CONNECTING before calling onDisconnected, in both places. Add a test.
- **M2. The v1.0.0 release guard refuses a correctly signed APK** (ci.yml:122-127). With minSdk 28, AGP signs with v2/v3 only, so there is no `META-INF/*.RSA` and the check falls through to `apksigner`. The glob `"$ANDROID_HOME"/build-tools/*/apksigner` expands to several paths on ubuntu-latest, so the command shifts its arguments and fails silently. CERT ends up empty and the job exits 1. Fix: pick one build-tools version (`ls -d …/build-tools/*/ | sort -V | tail -1`). Better still, compare the certificate's SHA-256 to a pinned fingerprint. If the name match stays, document that the key's DN must contain "GameCtl" (case-sensitive).
- **M3. The companion can lose every paired phone on a transient read error** (CompanionConfig.cs:107-121). Any exception from ReadAllText, such as an antivirus or OneDrive lock, loads defaults. Under the same lock the copy-aside usually fails too. The next Update then overwrites the real file. Fix: retry IOException on read, set the file aside only on JsonException, and never save over a file that couldn't be read or set aside.

## Minor (open)
### Android
- **A18.** "No foreground service" isn't stated anywhere a user reads. Add to RELEASE_NOTES Known limits: "Keep GameCtl on screen while playing. Leaving it releases every control."
- **A19-a.** If another PC now has the old IP, `nameOf` falls back to `lastServerName` (CompanionClient.kt:379), and the bad_token reply clears the old PC's token (:172). Look up names only from discovery.
- **A19-b.** The auto-follow in addServer checks for DISCONNECTED or ERROR, but startDiscovery turns those into SEARCHING (:308). So it only fires from the CONNECTING loop.
- **A19-c.** Liveness still counts only pongs (:152, :181). Fix it or reply with a reason.
- **A25.** The action-level "0 to 1 / -1 to 1" chips still show for sliders and joysticks and now do nothing (ActionEditor.kt:170 via Inspector.kt:138/148/150). Hide them.
- **R6-2 follow-up.** In Utility vehicles, button 24 is both Travel speed and Spreader, and 25/26 are both Engine speed and Spread rate. That breaks "one number per job" (BuiltInProfiles.kt:281). Use free numbers, or change the comment and the INSTALL sentence.
- **R6-4.** `import` decodes before it checks the schema (Profiles.kt:37-38), so a newer file still gives "ControlType does not contain element…". Read `schema` from parseToJsonElement first, and fix the comment at :22-24.
- **Signing fallback** (build.gradle.kts:28). An empty `GAMECTL_KEY_ALIAS` skips the `?: "gamectl"` default; use `takeIf { it.isNotBlank() }`. The key password is assumed to equal the store password: say so in INSTALL, or add a variable for it.
- **versionCode.** 1.0.0 uses versionCode 1, the same as beta-1. Use 2 or more so logs and the footer can tell them apart.

### Windows
- **W-1.** ScanBluetooth swallows Present() exceptions (CompanionHost.cs:266), so "Bluetooth scan failed" never logs. Also: after a timed-out Dispose wait, a scan in flight still raises BluetoothChanged, and `_stop` isn't disposed.
- **W-2.** FileLog grows without limit when rotation fails (FileLog.cs:34, 55). A probe reached 2.6 MB with a 1 MB cap. Stop writing, or truncate, when `.1` can't be replaced.
- **W-3.** With `--allow-second`, both processes append to and rotate the same log. Use a separate log for the second instance, or none.
- **W-4.** A newer settings file (schema 2) is silently downgraded and loses its unknown fields (CompanionConfig.cs:58). Leave it untouched and warn.
- **W-5.** The pairing PIN is written to companion.log (CompanionHost.cs:186), and INSTALL asks users to attach that log. Mask it.
- **W-6.** Directory.Build.props sets Version, AssemblyVersion, FileVersion and InformationalVersion separately, and the UI reads AssemblyVersion. Derive all of them from `$(Version)`.
- **W-7.** The window title "GAMECTL Companion" (MainWindow.axaml:8) shows in the taskbar and Alt-Tab. Use "GameCtl Companion" and keep the capitals for the in-window header.

### BeamNG mod
- **B-1.** When socket.tcp() fails, the "one warning" repeats every 3 s (Link.lua:774-775), which contradicts EXPECTED.md:23. Warn once.
- **B-2.** EXPECTED.md:26-33 doesn't list the new "could not create a socket" line.
- **B-3.** The first retry after Ctrl+E, a spawn or Ctrl+R now waits 5 s instead of 0.5 s (Link.lua:858, attachedId set at :403). That breaks "lamps match within a second". Keep 0.5 s for the first retry.
- **B-4.** The level-unload test doesn't prove the hook. Stubbing out onClientEndMission (Link.lua:889) still passes all 172 tests. Add an assertion that depends on it.
- **B-5.** README.md:58 still says the tests use "one vehicle VM". There are now three.
- **B-6.** protocol/PROTOCOL.md:1 is still titled "Game Controller protocol".
- **B-7.** info.json `tagid: "gamecontroller"` is a repository id field. Drop it unless ALI confirms in game that the Mods list shows no repository or update state for it.

## Closed this round
- **Fixed (verified):** rc1-docs 1-12, R5-1, R5-3, R6-1, R6-3, R6-5, A25 core, B-S2, B-S3, and the mod's level-change snapshot, silent-vehicle retry, trailer guards and GameCtl naming. The companion's Run key quoting, quit, single instance, manifest and CI secret handling all check out.
- **Accepted as documented limits:** B-S1 (beamng-mod README, Known limits; RELEASE_NOTES), English-only text (RELEASE_NOTES:52), and A18's foreground service, once the line above is added.

Not verifiable here: real Bluetooth, vJoy, ViGEm and SendInput output, and BeamNG in game. ALI's beta tests cover these.

**Open count: 26 (3 MUST, 23 minor).**
