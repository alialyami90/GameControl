# 1.0 docs pass (dev 5bfdf08): README.md, docs/INSTALL.md, docs/SMOKE_TEST.md read as a first-time user

Overall the install path is now clear and matches the app. The MUSTs below are about the jump from beta to 1.0. They are things a new user, or a beta tester upgrading, would trip on.

## MUST for 1.0
1. **Upgrading from a beta loses profiles unless the user exports them first.** The betas are debug-signed. A 1.0 APK with a release key won't install over them: Android says "App not installed" (signature mismatch). The user has to uninstall, which deletes their profiles and paired PCs. Add an "Upgrading from a beta" box at the top of INSTALL section 1: export custom profiles (card menu > Export to file), uninstall the beta, install 1.0, import, and pair the PC again. The companion's paired-phone list survives, but the phone's token is gone, so the PIN is needed once. Put the same note in the release notes.
2. **The "beta" and "test key" wording is still there.**
   - INSTALL:19 says "Every beta is on the GitHub releases page".
   - INSTALL:28-29 and SMOKE 0.1 say "The beta is signed with a test key… Play Protect may warn".
   - SMOKE:11 says "The beta files" and SMOKE 0.0 says "pick the newest beta".

   Change these to "the latest release". Reword the Play Protect line to match the signing ALI picks. A sideloaded APK with a release key can still get a Play Protect prompt, so keep a softer version of it.
3. **Built-in profile count.** SMOKE 0.1 lists five built-in profiles. 1.0 has seven, adding **Bulldozer** and **Utility vehicles**.
4. **No test for the 1.0 additions.** Add two things to SMOKE_TEST:
   - **A2a (ALI's beta-1 bug).** With the companion running and Wi-Fi not connected, its Phones list shows the phone with a **BT** tag within about 2 s of Bluetooth pairing, plus the amber "Wi-Fi: Not linked" banner. Turning Bluetooth off removes it.
   - **Part F, machines (5 min).** Open **Bulldozer** with joy.cpl on GameCtl Pad. Move **Drive / steer**: X and Y follow, and letting go centres both. Move **Blade lift / tilt**: RY and RX follow. Hold a stick and switch page with the other hand: the axes centre. Then in **Utility vehicles** check each page's buttons light in joy.cpl, especially any button above 32 if R6-2 isn't renumbered.
5. **The building section doesn't match release signing.** INSTALL:97 says `:app:assembleRelease` writes `app-release.apk`. With a release signing config, that needs the keystore. Say what happens without it, either an unsigned APK or a fallback to the debug key, and which environment variables or `keystore.properties` CI uses. Never put the key itself in the repo.
6. **README has no way in for a non-developer.** It never links the releases page, gives no requirements (Android 9+, Windows 10/11 64-bit), and has no version. Add a "Download" line with the releases URL, the requirements, "Version 1.0.0", and one screenshot of the play screen. Also add a LICENSE or say there isn't one. Users ask.

## SHOULD (post-1.0 is fine)
7. **One product name.** README and INSTALL say "GameCtl". The Windows exe is `GameControllerCompanion.exe` and its window says "GAME CONTROLLER Companion for Windows". The Bluetooth device is "GameCtl Pad". Pick one name for 1.0, or at least add a sentence in INSTALL: "the companion's window is titled GAME CONTROLLER".
8. **SMOKE B1 still says "open the connection chip".** Use "the PAIRED / OFF badges" as in A1 and INSTALL.
9. **A troubleshooting section in INSTALL.** Cover these:
   - The PC isn't found: guest Wi-Fi or AP isolation, a VPN on the PC or phone, the network set to Public, or the phone on mobile data.
   - The PIN is rejected.
   - Bluetooth pairs but nothing moves in joy.cpl: re-pair, or remove GameCtl Pad from Windows and add it again.
   - No lamps in BeamNG: check the mod is enabled in the mod manager.
   - Where the companion keeps its config and log, for bug reports.
10. **Start the companion with Windows.** If the companion has a setting for it, say so in 2b. If not, mention a shortcut in `shell:startup`.
11. **Machine profiles note (INSTALL:90).** If R6-2 isn't fixed, say vJoy must be configured with at least 36 buttons, and that some games only see 32. INSTALL:59 already says 128 for vJoy, which covers it on that route.
12. **Uninstall.** Say how to remove everything: uninstall the app, delete the companion folder and its config folder, remove `gamecontroller.zip` and the `_gamecontroller.json` input map, and remove GameCtl Pad from Windows Bluetooth.
