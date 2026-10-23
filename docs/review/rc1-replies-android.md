# Lead developer: RC1 replies (Android, docs, release)

## Android backlog
- A16 Closed by earlier work: replayHeld re-sends held momentary/sliders and setter latches after welcome and HID connect (A23 guard), and rehomeLatches (A-S1) re-presses latches whose route moved.
- A18 Closed: Bluetooth ACTION_STATE_CHANGED receiver re-registers HID, onGetReport returns the live report, BLUETOOTH_SCAN removed. No foreground service by design: the app releases every input when it leaves the screen (onPause), so there is nothing to keep alive in the background.
- A19 Fixed: tokens are also saved under the PC's name; open() falls back to it (only when no PIN is being entered), bad_token clears both, and discovery reconnects automatically when the last PC reappears on a new address. ProfileRepository.save already writes on an IO executor; protocol/examples/ exists.
- A25 Fixed: a slider's control range is the single truth: InputEngine.isUnipolar and Protocol.effectiveAction both take it from the control. Test a25_sliderRangeComesFromTheControl.
- A26 Fixed: CompanionClient.disconnect() calls onDisconnected when it was connected, so the engine forgets companion holds.
- R6-3 Fixed (test joystickProfilesExportAsSchema2AndKeepActionY). R6-4 Fixed (schema 2 for profiles with joysticks). R6-5 Fixed (joystick indicator from its axes; test r6_5_joystickIndicatorFollowsItsAxes).
- Hard-coded English strings: accepted as a documented limit for 1.0 (RELEASE_NOTES "Known limits": English only).

## rc1-docs
1. Fixed: INSTALL section 1 "Upgrading from a beta" box; same in RELEASE_NOTES.
2. Fixed: no "beta"/"test key" wording left in INSTALL or SMOKE; softer Play Protect line.
3. Fixed: SMOKE 0.1 lists seven profiles.
4. Fixed: SMOKE A2a (Bluetooth-only BT tag, "Bluetooth only", WI-FI Not linked banner) and Part F (machines).
5. Fixed: INSTALL "Building from source" explains env-var signing, the debug fallback, the CI secrets and the v* guard.
6. Fixed: README has Download, requirements, "Version 1.0.0", a screenshot (docs/images/play-beamng.png) and a License section.
7. Fixed: one name, GameCtl: GameCtlCompanion.exe, window "GAMECTL Companion", mod "GameCtl for BeamNG", Bluetooth "GameCtl Pad".
8. Fixed: SMOKE B1 uses the PAIRED / OFF badges.
9. Fixed: INSTALL Troubleshooting section (network, PIN, Bluetooth, sound, BeamNG lamps, where the log is).
10. Fixed: INSTALL 2b step 4 (Start with Windows setting, tray and QUIT COMPANION).
11. Fixed by R6-2 (all machine buttons are 32 or below); INSTALL says so.
12. Fixed: INSTALL Uninstalling section.
- R5-3 Fixed: INSTALL Bluetooth step 3 multi-phone note.
