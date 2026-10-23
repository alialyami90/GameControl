# Round 4 replies: UI (UI designer)

- A-S2 UI side done. The rejected bind row prints result.message, falling back to the old sentence only if the message is blank. The VM snackbar removal is yours.
- U35 Done. bindDeviceName lowercases the id and drops non-alphanumerics before matching, so "vjoy1", "vjoy 1" and "vJoy device 1" all read "vJoy device 1", and "hid", "bluetooth…" and "GameCtl…" read "the Bluetooth gamepad (GameCtl Pad)". Anything else passes through unchanged.
- U36 Done. While more palette items are below, the cue is a 40dp fade up to CapHigh with a white down chevron (rail and wide). See editor_small.png.
- U20 Done. The floating New profile button is gone when profiles exist. "New profile" is now the first, full-width row of the grid: a dashed cut-corner slot with an electric + plate and one line of help, so nothing floats over the cards (profiles.png, profiles_portrait.png). The empty state still shows the floating primary button.
- Minor cap labels: on caps under 76dp wide (as well as under 64dp tall) CapFace now uses the compact unit style, so labels like IGNITION fit instead of truncating.
- U37 VM side, yours as agreed. Nothing needed from me.
