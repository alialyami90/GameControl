# Round 1 replies: Windows companion

All fixes are in `windows/`. `dotnet build`, `dotnet test` (104 tests, all green) and `dotnet publish -r win-x64` pass.

## MUSTs

- **W1, fixed.** The engine now tracks held BeamNG commands (`BeamNgCommands.IsHeld`: `horn`, `starter`, `recover`, `action.*`) per control and sends value 0 for them on session end (close, idle timeout, replaced, forgotten, "Release all input", host Dispose), on a re-sync that removes or changes the binding, and in the 1000 ms momentary auto-release. Tests: `Held_command_gets_zero_on_session_end`, `Silent_momentary_horn_is_auto_released`, `Changed_binding_releases_held_command`.
- **W2, fixed.** `SetBindings` releases only ids whose binding was removed or changed shape (kind, route, action, positions, cw/ccw) and resets their edge state; unchanged ids keep their latched holds and their sequence numbers. Tests: `Unchanged_latched_hold_survives_resync`, `Changed_binding_releases_its_hold_on_resync`, `Removed_binding_releases_its_hold_on_resync`, `Sequence_table_survives_resync`.
- **A1, done.** The value check is gone (`ApplyResult.Repeat` removed). Latching events apply in their own direction every time (hold: down/up; tap: tap; press, macro and toggle commands: fire on an "on" event; setters: send the value). Switch and radio fire the selected position on every new sequence number.

## The rest

- **W3, done.** mDNS advertises and joins only addresses of adapters with a default gateway (falls back to all when none has one) and sends each packet once per adapter with `IP_MULTICAST_IF`.
- **W4, done.** Sessions silent for 8 s (`HostOptions.IdleTimeout`) are closed and released. UDP input counts as activity. PROTOCOL.md line 58 says 6 s; the phone uses 6 s to give up, so 8 s on the PC leaves margin. Lead: change the spec to 8 s or tell me to use 6.
- **W5, done.** Each control remembers the axis it last moved; session end parks bipolar vJoy at 0.5, unipolar at 0, Xbox sticks and triggers at 0. Profile re-sync does not park axes (a slider keeps its value). Test: `Session_end_parks_axes`.
- **W6, done.** Per-message try/catch in the WebSocket loop replies `bad_message` and keeps the session; router axis paths and `ViGEmOutput.Pad()` are guarded.
- **W7, done.** `Tick` has an overlap guard (`Interlocked`) and a try/catch that logs.
- **W8, done.** On mod disconnect phones get `{"type":"state","source":"beamng","values":{},"full":true}`, then a full OutGauge snapshot if telemetry is live. Asserted in `BeamNG_state_commands_and_bindings_are_relayed`.
- **W9, done.** The session is registered after `welcome` is sent.
- **W10, done.** `dashLights == 0` means every mapped lamp is trusted. Hazard debouncing is left to the phone as suggested. Needs an in-game check.
- **W11, done.** `PushCapabilities` is locked.
- **W12, done.** Token lookup reads devices under the store lock (`ConfigStore.Read`); a corrupt hash matches nothing instead of throwing.
- **W13, done.** `Refresh` only lists free or owned devices; a device is acquired, reset and centred on its first use. A device another feeder takes is dropped from our list.
- **W14, done.** Upgrades carrying an `Origin` header get 403. Test: `Browser_origin_is_refused`. Android (OkHttp) sends no Origin; a future web client would need a token-based exception.
- **W15, done.** `BeamNgLink` writes through a bounded channel and a writer task (Dispose flushes for up to 500 ms so shutdown 0s arrive). OutGauge's local-address check is cached for 10 s. README documents the UDP 4444 clash with SimHub and the two ways around it.

## Choices to note

- Setters (`lights.low`, `hazard`, ...) are not zeroed on disconnect; only held commands are. Turning the lights off because the phone's Wi-Fi blinked would be worse than leaving them.
- Latching plus a toggle command: a toggle fires only on an "on" event (per PROTOCOL.md, rising edge), so a latching control bound to `*.toggle` flips the game on and does nothing on "off". The built-in profiles use setters, which is the right binding for latching. Lead: either keep the rule and have the profile editor steer latching controls to setters, or say "toggle fires on every latching event" in the spec and I'll switch (one line).
- Not verifiable on Linux: real SendInput/vJoy/ViGEm behaviour, mDNS next to Windows' own responder, per-adapter multicast on a multi-NIC PC.
