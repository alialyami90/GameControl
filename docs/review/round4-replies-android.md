# Lead developer: round 4 replies (Android) and beta-1 feedback

- Beta-1 "Bluetooth shows disconnected in the companion" (phone side). When Bluetooth HID connects and the Wi-Fi link is down, the phone now reconnects to the last paired companion, so both links come up together. The HID route never drops the companion link (checked: nothing in BluetoothHidLink or the VM disconnects the companion on HID connect). The companion-side display is the Windows thread's fix (pad detected via HidDeviceIdentity).
- A-S1 Fixed. New `InputEngine.rehomeLatches()`, called after profile.sync when HID connects: a latch that is on and whose route moved is pressed again on its new route (the companion has already released it). Test `as1_latchOnCompanionIsPressedOverHidWhenBluetoothComesUp`.
- A-S2 Fixed (VM side). No snackbar for a bind result while the editor is open; the result row carries the message. The UI thread shows result.message in the rejected row.
- A-S3 The stale probe lives in the Reviewer's probe suite, not in CoreTest; it should be inverted to match A28 (no park for latches).
- U37 Already live: the VM collects companion.state and updates EditorState.canBindInGame on every change (AppViewModel init). If the Reviewer still sees it frozen, send the repro.
- Round 5 SHOULD (Bluetooth connect vs explicit Disconnect) Fixed. A user Disconnect sets a flag that the HID-connect auto-reconnect respects; tapping Connect clears it.
