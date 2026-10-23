# Round 1 replies: Android (Lead developer)

All on branch `dev`, CI green (run 39811881907). Tests added for each MUST in `core/src/test/.../CoreTest.kt`.

## MUST
- **A1** Done. `InputEngine` no longer compares values; `AppViewModel.syncValuesFromGame` calls `engine.syncValue(id, v)` whenever game feedback moves a latch/switch, so the next press is judged against what the game shows. PROTOCOL.md "Duplicates" now says dedupe by `s` only, never by value (companion dev asked to drop the value check in BindingEngine). Test `latchRepeatsAfterGameChangedState`.
- **A2** Done. `onControlValue` blocks only presses on unavailable controls; releases always pass (`InputEngine.isRelease`).
- **A3** Done. Releases go out on the route the press used (`routeOf`), and a release with nothing held goes nowhere. New `engine.onLinkChanged()` is called on HID connect/disconnect and companion disconnect: HID loss clears `HidState` and forgets HID-routed holds; HID connect sends clean keyboard/gamepad reports. Test `releaseFollowsTheRouteOfItsPressAndLinkLossClearsHid`.
- **A4** Done. `resetAxes()` on profile set, link change and `releaseAll` puts unipolar axes at -1 (0 %) and bipolar at centre; `isUnipolar()` honours control or action. Test `unipolarAxesRestAtMinimumOverHid`.
- **A5** Done. `Protocol.json` has a `SerializersModule` with `polymorphic(Action) { defaultDeserializer { Action.None } }`. Test `unknownActionTypeImportsAsNone`.
- **A6** Done. `LayoutOps.sanitize` dedupes control ids and page ids across the whole profile. Test `importDedupesIdsAcrossPages`.
- **A7** Done. `Protocol.effectiveAction()` emits the axis action as unipolar whenever the control is unipolar, so HID and companion agree.
- **A8** Done. `onServiceDisconnected` now reports the link down (`onConnectionChanged(false)`) and shows a message.

## SHOULD done
- A9 reconnect runnable cancelled and old socket closed before `open()`; A10 UDP open checks `generation` and `isConnected` on the main thread before adopting the socket; A11 `version_mismatch` replies, closes and stops reconnecting; A12 IPv6 from NSD is bracketed and `connect()` parses `[v6]:port`, stale delayed `stopDiscovery` no longer cuts a newer scan short; A13 `releaseAll` cancels tap/macro jobs and `finally` releases their keys; A14 HID button pulses are queued; A15 resend loop survives a brief drop; A17 ErrorRollOver past 6 keys; A20 push-to-talk moved to F13, MudRunner layout fills the grid (keys still the game defaults, user-editable).

## Not done yet (round 2)
- A16 re-send latched holds/sliders after a new `welcome` or HID reconnect.
- A18 Bluetooth lifecycle receiver, foreground service, `onGetReport` with the live report, drop `BLUETOOTH_SCAN`.
- A19 token keyed by IP, save on main thread, `protocol/examples/`.
