# rc1 – UI designer replies (1.0 polish pass)

Renders: `.impeccable/review/rc1/` (all seven built-ins at phone 914dp, 640x360 every page, tablet; Profiles first-run and linked at phone, 640, tablet, portrait; Connect; Editor).

- **Launcher icon final.** `drawable/ic_launcher_foreground.xml` is now a cut-corner control cap with a magenta racing stripe and a steering wheel, inside the 66dp safe zone. `colors.xml`: `gc_background` #0A0D14 (Race.Ground), `gc_accent` #FF2E7E (Race.Electric). Render: `rc1_launcher.png` (square and circle mask).
- **Themed icon.** New `drawable/ic_launcher_monochrome.xml` (outline cap and wheel, alpha only) wired as `<monochrome>` in `mipmap-anydpi-v26/ic_launcher.xml`.
- **Version line.** New `ui/AppInfo.kt` (`internal val AppVersion = BuildConfig.VERSION_NAME`). Profiles ends its list with a quiet centred "GameCtl 1.0.0" footer. Render: `rc1_profiles_about.png`.
- **First-run Connect lead-in.** While neither Bluetooth nor the PC companion is connected, Profiles leads with a full-width "CONNECT TO YOUR PC" row (both link icons, one line on what each route gives, primary Connect button; the whole row opens Connect). It disappears once either link is up. Also shown above "No profiles yet" in the empty state. Renders: `rc1_profiles_first_*.png` vs `rc1_profiles_linked_*.png`.
- **No truncated cap labels at 640x360.** Small caps wrap the label to two compact lines and shrink the icon slightly, so SHIFT DOWN, CENTER DIFF, PARK BRAKE, WORK LIGHTS, OUTRIGGERS OUT read in full instead of "SHIFT ...". Renders: `rc1_builtin_*_small_*.png`.
- **One-column slider headers.** Below 104dp the label stacks over the readout (two compact lines) instead of "THROT... 0". Wider sliders keep the side-by-side header.
- **Joystick titles wrap.** Two-part names (BLADE LIFT / TILT, PLOUGH LIFT / ANGLE, TOW BOOM / EXTEND) take two lines instead of an ellipsis.
- **Play HUD profile name at 640dp.** Tight mode prints the name in the condensed cap style on up to two lines (BEAMNG.DRIVE, UTILITY VEHICLES) instead of "BeamN...". The game tag is dropped in tight mode only.
- **Checked, no change needed:** phone and tablet renders of all seven built-ins, Connect at 640 and tablet, Editor at 640 with inspector and tablet with inspector. Targets stay at 48dp (unchanged since round 4) and text contrast uses Ink/InkMuted/InkFaint on Ground and Panel as before.
