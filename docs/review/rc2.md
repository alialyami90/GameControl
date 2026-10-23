# 1.0 release candidate 2 review (dev 32db5b4, CI run 39891529164 green)

**Verdict: not ready. 9 comments are open: 1 MUST and 8 minor.** All 3 RC1 MUSTs are fixed, and 23 of the 26 RC1 items are closed. Tests pass: Android core 33/33, Windows 133/133, BeamNG mod 175/175 on 5 runs. The mod zip is identical to source.

## MUST (open)
- **M4. A repeated key in companion.json crashes the companion at start-up. This is new in RC2** (CompanionConfig.cs:142, 145). Walking the parsed JsonObject throws ArgumentException on a duplicate key, for example `"serverName"` twice. The new catch filter (JsonException, NotSupportedException, InvalidOperationException) doesn't include it, so the ConfigStore constructor throws. RC1's catch-all handled it. Fix: add ArgumentException to the filter, or detect `schema` with a Utf8JsonReader. Add a test.

## Minor (open)
### Android
- **A-1. Known-limit wording is wrong.** RELEASE_NOTES.md:48 says "Leaving it releases every control". But onPause → releaseAll only lets go of buttons and sliders (InputEngine.kt:242-246), and toggles stay on at the PC. Suggested text: "Leaving it lets go of held buttons and sliders; toggles stay on."
- **A-2. Auto-follow can override the PC the user picked** (CompanionClient.kt:390-392). The setup: the last PC is off and the app is retrying it. The user taps PC B, and the last PC then shows up in discovery within 8 s. The app switches back to the last PC. Only follow when `host == parseHostPort(settings.lastHost).first`.

### Windows
- **W-8.** The read retry waits 1.5 s in total, not the "about 3 s" the reply says, and it runs on the UI thread at start-up (CompanionConfig.cs:132). Make the wait match the reply, or correct the reply. Then move the retry off the UI thread, or keep the 1.5 s and say so in a comment.
- **W-9.** When saving is blocked, the Settings note still says "Try again in a moment" (MainViewModel.cs:534), but a retry can't succeed before a restart. Match the banner's wording.
- **W-10.** A second instance shows companion.log as its log path (MainViewModel.cs:79). It should show companion-second.log.
- **W-11.** The ScanBluetoothLoop doc comment sits on the `_lastScanError` field (CompanionHost.cs:241-245). Move the field above the comment.

### BeamNG mod
- **B-3 (still open). The quick retry is lost after the second Ctrl+R on the same car.** `attachTries` (Link.lua:406) is reset only in onClientEndMission (:902), not when the car answers. From the second Ctrl+R of a session, a lost first load takes 5 s to recover. Fix: reset `attachTries = 0` where the car answers (next to `vehicleId = id`, :366). Add a test that runs Ctrl+R twice.
- **B-8. The once-only socket warning has no test.** Breaking the `socketWarned` flag (Link.lua:780-781) still passes all tests. Add one.

## Closed this round
- **Fixed (verified):**
  - M1 (A16/A26), M2 (CI guard with one apksigner, pinned SHA-256, fails closed), M3 (companion settings never overwritten after a failed read).
  - A19-a/b/c, A25 chips, R6-4, signing fallback, versionCode 3.
  - W-1 to W-7.
  - B-1, B-2, B-4, B-5, B-6, B-7.
- **Accepted as documented limits:**
  - A18, with the RELEASE_NOTES wording corrected in A-1.
  - R6-2 shared job numbers: INSTALL.md:102, "Jobs only one machine has can share a button with another machine's own job, so bind each machine on its own."
  - English-only text, and B-S1, as in RC1.

Not verifiable here: that the pinned fingerprint is the real release key, plus real Bluetooth, driver output and BeamNG in game.

**Open count: 9 (1 MUST, 8 minor).**
