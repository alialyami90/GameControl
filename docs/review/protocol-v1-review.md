# Reviewer: protocol v1 findings

Overall solid. Fix MUST 1-4 before the components lock in.

## MUST
1. **Lost UDP datagrams desync state-changing events.** Latching (key tap / beamng .toggle), switch/radio, rotary detents and macros are sent once, so one dropped datagram leaves the game out of sync with the phone. Send these discrete events over the WebSocket (reliable) and keep UDP for momentary holds and sliders only. Alternatively, resend them and make the companion idempotent: apply a latch/switch only when `val` differs from the last applied `val` for that id.
2. **Momentary + toggle-style beamng commands fire twice.** A momentary button bound to `lights.low.toggle` sends 1 then 0, so the mod toggles twice. Specify that toggle and one-shot commands fire only on the rising edge, and only `starter`, `horn` and `recover` pass the value through. For latching beamng controls, prefer an idempotent `lights.low` with value 1/0 over `.toggle`.
3. **The sequence-number scope across reconnects is undefined.** State that `s` and the per-id last-seen table reset with each new session key from `welcome`.
4. **A `radio` Binding example is missing.** Say that it uses `positions` like `switch`. Also define the value semantics for `beamng` actions bound to slider/rotary, or forbid them.

## SHOULD
5. Rate-limit PIN attempts. Define the multi-phone policy, and say that the companion releases only the inputs held by the phone that disconnected.
6. Forward compatibility: receivers ignore unknown fields and message types, `bad_message` is only for malformed JSON, and an unknown action type is treated as `none`.
7. The Profile import/export file format (pages, layout grid, visibleWhen, icon, indicator key) is unspecified. Add a versioned profile schema plus an example in protocol/examples (CLAUDE.md requires JSON import/export).
8. OutGauge mapping: gear is an int (0=R, 1=N, 2+=gear) and lights are a DashLights bitmask. Document the mapping to `gear`, `lights.*`, `signal.*`, `parkingbrake`, `tcs.active` and `abs.active`.
9. `bindings.apply` with `device:"vjoy1"`: BeamNG keys input maps by device product/GUID (settings/inputmaps/*.diff). Mark it (verify), and have the mod resolve the vJoy device by name.
10. The "off-centre" resend rule for a unipolar slider is ambiguous. Define centre as 0, or resend any slider that moved in the last 2 s.
