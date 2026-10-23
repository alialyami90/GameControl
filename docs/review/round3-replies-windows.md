# Round 3 replies: Windows companion

All changes are in `windows/`. `dotnet build` gives 0 warnings, `dotnet test` passes 115/115 (5 new tests), and `dotnet publish -r win-x64` builds the exe. The screenshots in `windows/docs/screenshots/` are re-rendered (11 PNGs).

## MUST

- **W35, fixed.**
  - `ConfigStore.Update` rolls the in-memory config back when the save fails, then rethrows. Memory never holds a device the file doesn't, so a failed pairing leaves no ghost in the Phones list.
  - `Pairing.Authenticate` returns a new outcome, `SaveFailed`. No token is issued, the PIN is not rotated (it stays valid for a retry), and the reason goes in `SaveError`.
  - The host logs an error naming the settings file and replies `{"type":"error","code":"config_write_failed",...}` before closing. **Lead: please add `config_write_failed` to the error codes in PROTOCOL.md.** The phone should show its message as a pairing failure, not a wrong PIN.
  - Tests: `Failed_save_pairs_nothing_and_keeps_the_pin` and `Pairing_with_an_unwritable_config_replies_an_error`.
- **W35 Forget, fixed.** `ForgetDevice` now removes, releases and closes the live session first, and only then persists. If the save fails, the phone is already stopped, its held input is released, and the exception still reaches the UI, which logs it. Test: `Forget_stops_the_phone_even_when_the_save_fails`.
- **W17 partial, closed by W35.** The PIN pairing path now reports instead of silently aborting.

## SHOULD

- **W36, fixed.** After 1 s of silence, `Tick` resets a momentary control's edge state whether or not it holds anything. A lost release can no longer swallow the next press of a toggle, a press-trigger key or a macro. Test: `Lost_release_does_not_swallow_the_next_toggle`.
- **W37, fixed.** When a binding changes while its control is pressed, the first event afterwards (the phone's resend) only syncs the state for one-shot actions: `tap` and `press` keys, macros, and toggle and one-shot commands. Hold actions still follow the level. Test: `Binding_edit_while_held_does_not_refire_on_the_resend`.
- **W38, fixed.** Save settings and Forget run their store write on `Task.Run`, so the locked-file retry never blocks the UI thread. The form values are read on the UI thread first.
- **W29 caveat, fixed.** `ConnectionChanged` and `Log` in `BeamNgLink` are now guarded too.

## U11 notes

- **Tokens are no longer duplicated.**
  - The signal path and the code-side palette (`Rows.cs`) read colours from the Rig.axaml resources through `Controls/Tokens.cs`. The hex values in code remain only as fallbacks for a missing resource.
  - The hard-coded `#FFC24D` is now the `AmberHover` token.
- **Extras for DESIGN.md (Lead / designer):** please add these to the frontmatter so the phone and PC stay one system, or tell me what to derive them from:

  | Token | Value |
  |---|---|
  | `electric-hover` | `#FF4F93` |
  | `electric-pressed` | `#E01F69` |
  | `amber-hover` | `#FFC24D` |
  | `on-amber` | `#241600` |
  | `electric-dim` | `#3A1326` |
  | `amber-dim` | `#2E2410` |
  | `green-dim` | `#0F2E20` |
  | `red-dim` | `#341414` |

  The dim colours are banner and badge backgrounds.
- **Narrow signal path.**
  - Nodes never go below 156 px, and the hub takes what is left (minimum 112 px).
  - Below 190 px a node shows its name on two lines (for example "vJoy / joystick") and drops the detail line. See `10-status-min-size.png`.
  - The page subtitle still wraps beside Release all input at 820 px. It wraps cleanly, so I left it.
- **Phones breakpoints fixed.** Columns are tighter and the breakpoints moved to 860 / 640 px of content. A 1120 px window shows every column including Paired (`07-phones-live.png`). 820 px keeps Phone, Status, Inputs and Forget (`11-phones-min-size.png`).
