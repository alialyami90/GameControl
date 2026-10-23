# Round 2 replies: Windows companion

All changes are in `windows/`. `dotnet build` gives 0 warnings, `dotnet test` passes 110/110, and `dotnet publish -r win-x64` produces a 45 MB exe. The new screenshots are in `windows/docs/screenshots/` (11 PNGs, including 820×560 views of Status and Phones).

## MUSTs

- **W16, fixed.** `AppDomain.UnhandledException` and a try/finally around the Avalonia lifetime both call `App.EmergencyRelease()`, which runs `ReleaseEverything()` and then an idempotent `Shutdown()`. Each shutdown step is wrapped separately, so one failing step never skips the release. `Dispatcher.UIThread.UnhandledException` logs the error and marks it handled. `TaskScheduler.UnobservedTaskException` is observed.
- **W17, fixed.** `ConfigStore.Save` retries a locked file 3 times (100 and 200 ms). Save settings and Forget catch the failure, show it in `SettingsNote` and the log, and don't crash. A failed "last seen" write no longer refuses a phone's token.
- **W27, fixed.** A changed or removed binding parks the axis it last moved, so a slider rebound from X to Z returns X to rest. Tests: `Rebinding_a_slider_parks_the_old_axis`, `Range_change_counts_as_a_changed_binding`.
- **W31, fixed.** A setter driven by a momentary control counts as held. It gets 0 on the 1 s auto-release and at session end. Latching setters stay as they are. Test: `Momentary_setter_is_held_and_zeroed`.
- **Toggle rule, implemented as specced.** A `*.toggle` fires on every event from a latching control and on the rising edge only from a momentary one. Test: `Toggle_on_latching_fires_on_every_event`.

## SHOULDs

- **W18.** Addresses under the PIN are re-read every 2 s. Only gateway-bearing adapters are shown, the same set mDNS advertises.
- **W19.** The firewall check runs on `Task.Run` (`CheckFirewallAsync`).
- **W20.** Axis activity is merged per (phone, control) and flushed at most 10 times a second. A moving slider updates its own row at the top of Recent input instead of adding new rows.
- **W21.** A second launch signals a named event, and the running copy shows its window.
- **W22.** The minimum size is now 820×560. On first show the window is clamped to the screen's working area and centred.
- **W23.** `WindowCloseReason.OSShutdown` and `ShutdownRequested` now shut down cleanly instead of hiding to the tray.
- **W24.** All host and router handlers are unsubscribed in `MainViewModel.Dispose()`, which runs before `_host.Dispose()`.
- **W25.** The firewall check reads the rule with `verbose` and requires this exe's path. "Allow" now deletes our old rule and adds a new one in a single UAC prompt.
- **W26.** Tray icons are loaded once and only swapped when the state changes. The Xbox row shows "Pad plugged in" once the pad connects (`IXInputOutput.InUse`). "Last seen" refreshes every 2 s.
- **W28.** Bindings are compared field by field with `ActionSpec.Signature()`, which covers range, trigger, device, macro steps and holds.
- **W29.** `BeamNgLink` catches any exception from a handler, logs it and keeps the link up. Handler failures in the WebSocket loop are logged only, with no `bad_message` sent.
- **W30.** The mod queue holds 4096 messages and drops the oldest when full, so the newest release always gets through.
- **W32.** The idle timeout uses `Environment.TickCount64` (`PhoneSession.LastMessageTick`).
- **W33.** Profile load parks new or changed axis bindings at their rest position (unipolar 0, bipolar centre). Test: `Profile_load_parks_new_axes_at_rest`. vJoy now asks the driver directly on first use, so a device another feeder just freed works without waiting for a refresh.
- **W34.** A pulsed held command (horn from a rotary) stays in the held set until its 0 is sent, so a session end during the 80 ms pulse still sends the 0. Test: `Pulsed_held_command_is_released_by_a_session_end_inside_the_pulse`.

## U11: Race Front-End livery

- The tokens in `Styles/Rig.axaml` now come from DESIGN.md: ground, panel, cap, line, ink, electric, and the amber, green and red signals. Drop shadows are gone. The stale line-1 comment is fixed.
- A new `Controls/Plate.cs` adds `Shapes.Slant` (lean 0.21) and `Shapes.Cut` (14 px cut, 6 px corners).
  - Buttons and rail tabs use slanted plates. The active tab is the rail's one electric element.
  - Panels and banners use cut-corner plates.
  - Badges are small slanted tags: cap colour for counts, amber when something waits on the user.
- Type is italic Barlow Condensed for titles, names and numbers. The PIN is an ExtraBold italic readout with tabular figures. Button labels are uppercase. Sentences stay upright Barlow. The four italic and ExtraBold TTFs were added to `Assets/Fonts`.
- On the signal path, nodes are cut-corner caps with a racing stripe when live, and the PC is a slanted plate. Sparks are electric going out and green when the game confirms. The legend, PIN hero and side-rail layout ALI picked are all kept. Connected lamps are green (linked), per the Signal rule.
- The app and tray icons were recoloured to the new palette.
- Narrow windows: the PIN hint drops under the PIN and the legend wraps under its title. The Phones table drops Paired below 960 px, and Profile and Last seen below 760 px.
- Not verifiable here: real Windows DPI behaviour and how the tray looks on a real taskbar.
