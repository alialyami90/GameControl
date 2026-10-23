# GameCtl protocol, version 1

Owner: Lead developer. Shared by the Android app (`android/`), the Windows companion (`windows/`) and the BeamNG mod (`beamng-mod/`). Change it only through the Lead developer so all three stay in sync. Machine-readable examples live in `protocol/examples/`.

Every message is UTF-8 JSON and carries `"v": 1`. A receiver that gets a higher major version than it supports replies with `error` code `version_mismatch` and closes.

Forward compatibility: receivers ignore unknown fields and unknown message types. `bad_message` is only for malformed JSON or a missing required field. An unknown action `type` is treated as `none`.

## Scope: a HID controller for any Windows game

The phone is a game controller for **any** Windows game. BeamNG.drive is one fully supported example, not the only target. Input reaches Windows by two routes, and a profile can use either or both:

1. **Bluetooth HID (no PC software needed).** The app registers with Android's `BluetoothHidDevice` profile (Android 9+) as a composite HID device named **"GameCtl Pad"**: a keyboard (report id 1, boot-style 8-byte report) and a gamepad (report id 2: 64 buttons, 8 axes X Y Z RX RY RZ SL0 SL1, 16-bit each). Windows pairs it like any Bluetooth controller and every DirectInput / raw-HID game sees it. `key` actions go out as keyboard usages, `vjoy_button` / `vjoy_axis` actions with `device: 1` go out as the HID gamepad's buttons 1-64 and axes. XInput-only games need the companion's ViGEm route.
2. **Wi-Fi companion.** The Windows companion receives the events below and injects them (scancode `SendInput`, vJoy, ViGEm). It also carries everything HID cannot: macros with precise timing, `beamng` game commands, more than 64 buttons, and **game feedback** back to the phone.

Both routes can run at once: buttons over Bluetooth HID, feedback and BeamNG commands over Wi-Fi. When a profile is in HID mode the phone still sends `profile.sync`, but marks each binding it routes over HID with `"route":"hid"`; the companion must not inject those (it still uses them to know which ids exist).

`bindings.apply` device names: `"vjoy1"` (first device whose product name contains "vJoy") or `"hid"` (the device whose product name contains "GameCtl Pad").

## Ports and transports

| What | Transport | Default port | Who listens |
|---|---|---|---|
| Control channel (pairing, profile sync, state, commands) | WebSocket, path `/ws` | TCP 47800 | Companion |
| Control events (low latency) | UDP datagrams, one JSON object each | UDP 47801 | Companion |
| LAN discovery fallback | UDP broadcast | UDP 47802 | Companion |
| BeamNG mod link | TCP, newline-delimited JSON | TCP 47803 on 127.0.0.1 | Companion |
| OutGauge telemetry fallback | UDP (BeamNG OutGauge format) | UDP 4444 | Companion |

## Discovery

1. **mDNS / NSD**: the companion advertises service type `_gamectl._tcp.` on port 47800 with TXT records `v=1`, `name=<PC name>`, `udp=47801`.
2. **Broadcast fallback** (for networks that block multicast): the phone sends `{"v":1,"type":"discover"}` to `255.255.255.255:47802`. The companion answers unicast with `{"v":1,"type":"discover.reply","name":"<PC name>","wsPort":47800,"udpPort":47801}`.
3. The user can also type an IP address manually.

## Pairing and session (WebSocket)

Phone connects to `ws://<pc>:47800/ws` and sends:

```json
{"v":1,"type":"hello","deviceId":"<stable uuid>","deviceName":"Pixel 8","appVersion":"1.0.0","pin":"123456"}
```

- First time: `pin` is the 6-digit code the companion shows in its tray window.
- After pairing: send `"token":"<token>"` instead of `pin`.

Companion answers:

```json
{"v":1,"type":"welcome","serverName":"GAMING-PC","token":"<long-lived token>","session":"<8-char session key>","udpPort":47801,
 "capabilities":{"keyboard":true,"vjoy":true,"vjoyDevices":[1],"vigem":false,"beamng":false,"outgauge":true}}
```

or `{"v":1,"type":"error","code":"bad_pin"|"bad_token"|"version_mismatch","message":"..."}` and closes.

PIN attempts are rate-limited: after 5 wrong PINs the companion refuses new pairing for 60 s (`error` code `rate_limited`). Several phones may be connected at once; each has its own session key, its own held-input table, and its own active bindings. When a phone disconnects, the companion releases only the inputs that phone was holding.

When a phone's session ends for any reason (close, 8 s of silence, error), the companion releases every input that phone holds: keys and buttons go up, every BeamNG level command it holds is sent with value 0, bipolar axes return to centre and unipolar axes to 0.

Keep-alive: either side may send `{"v":1,"type":"ping","t":<ms>}`; the other replies `{"v":1,"type":"pong","t":<same>}`. The phone pings every 2 s and treats 6 s of silence as disconnected; the companion treats 8 s of silence from a phone as a dead session and releases its inputs. The companion releases that phone's held inputs when its WebSocket closes.

When `capabilities` change (BeamNG mod connects or disconnects, vJoy appears), the companion sends a fresh `{"v":1,"type":"capabilities","capabilities":{...}}`.

## Profile sync (phone to companion, WebSocket)

When a profile becomes active, and whenever it is edited, the phone sends the bindings for every control on every page. The companion is stateless about layout; it only needs to know what each control id does.

```json
{"v":1,"type":"profile.sync","profileId":"beamng-default","game":"beamng","bindings":[ <Binding>, ... ]}
```

Companion replies `{"v":1,"type":"profile.ack","profileId":"beamng-default","count":42}`.

### Binding

```json
{"id":"lights_low","kind":"latching","action":<Action>}
{"id":"gear_mode","kind":"switch","positions":[<Action>,<Action>,<Action>]}
{"id":"throttle_hand","kind":"slider","action":<Action>}
{"id":"drive_mode","kind":"radio","positions":[<Action>,<Action>,<Action>]}
{"id":"zoom","kind":"rotary","cw":<Action>,"ccw":<Action>}
```

`radio` uses `positions` exactly like `switch`.

`kind` is one of `momentary`, `latching`, `switch`, `radio`, `slider`, `rotary`.

### Action

```json
{"type":"none"}
{"type":"key","keys":["LCTRL","H"],"trigger":"hold"}
{"type":"vjoy_button","device":1,"button":12}
{"type":"vjoy_axis","device":1,"axis":"SL0"}
{"type":"macro","steps":[{"keys":["E"],"holdMs":50},{"delayMs":120},{"keys":["LSHIFT","E"],"holdMs":50}]}
{"type":"beamng","command":"lights.low.toggle"}
{"type":"xinput_button","button":"A"}
{"type":"xinput_axis","axis":"LT"}
```

- `key.keys`: names from the key table below. Modifiers first. The companion sends them with `SendInput` **scancodes** (`KEYEVENTF_SCANCODE`, plus `KEYEVENTF_EXTENDEDKEY` for extended keys).
- `key.trigger`:
  - `hold`: keys go down while the value is >= 0.5 and up when it drops below.
  - `tap`: on every value change, press and release (about 40 ms). Used for latching toggles where the game toggles on each key press.
  - `press`: tap only on the rising edge (0 to 1).
- `vjoy_button`: button down while value >= 0.5. Devices 1-16, buttons 1-128.
- `vjoy_axis`: `X`,`Y`,`Z`,`RX`,`RY`,`RZ`,`SL0`,`SL1`. Value -1..1 maps to the full axis range (bipolar). A unipolar slider sends 0..1 and the phone sets `"range":"unipolar"` on the action; then 0..1 maps to the full range.
- `macro`: runs once on the rising edge. Steps run in order.
- `beamng`: forwarded to the BeamNG mod as a `command` (see below). If no mod is connected the companion ignores it. Edge rules, applied by the companion:
  - `*.toggle` commands and one-shots (`reset`, `gear.up`, `camera.next`, ...) are sent to the mod on the rising edge only (value goes from < 0.5 to >= 0.5) when driven by a `momentary` control, and on **every** event (each value change, 1 and 0) when driven by a `latching` control, so a latch and a toggle stay in step. Prefer setters for latching controls anyway; the editor steers towards them.
  - **Held commands**: `starter`, `horn`, `recover`, every `action.<name>` command, and any setter driven by a `momentary` control (flash-to-pass = momentary `lights.high`). They pass the value through on every change, and the companion sends them with value 0 on the 1000 ms auto-release and at session end. A setter driven by a `latching` control is not zeroed (the latch is the user's intent).
  - Level commands pass the value through on every change: `starter`, `horn`, `recover`, every `action.<name>` command, and the idempotent setters `<feature>` with value 1/0 (for example `lights.low` with 1 = on, 0 = off). Built-in latching controls use the setters, so a resend never flips state.
  - On a `switch`/`radio`, the command at the selected position is sent with `value` = 1 (a setter) or once (a toggle/one-shot). `gearbox.mode` gets `value` = index.
  - On a `slider`, the command is sent with the raw value on every change (no built-in command uses this yet).
  - On a `rotary`, the `cw`/`ccw` command is sent once per detent.
- `xinput_*` (optional, ViGEm): buttons `A B X Y LB RB BACK START LS RS DPAD_UP DPAD_DOWN DPAD_LEFT DPAD_RIGHT GUIDE`; axes `LX LY RX RY LT RT`.

### How each control kind sends values

| kind | value sent | companion behaviour |
|---|---|---|
| momentary | 1 on press, 0 on release | apply `action` with the value |
| latching | 1 when latched on, 0 when off | apply `action`; for `key` actions the profile normally uses `trigger:"tap"` |
| switch / radio | the selected position index (0, 1, 2...) | tap the action at `positions[index]` (key: tap; vjoy_button: pulse 80 ms; beamng: per the edge rules above, setters get `value` = 1 and `gearbox.mode` gets the index) |
| slider | -1..1 (bipolar) or 0..1 (unipolar) | set the axis |
| rotary | +1 per clockwise detent, -1 per counter-clockwise detent | tap `cw` or `ccw` once per detent |

A `switch`/`radio` position bound to `vjoy_axis` sets that axis to index/(n-1) of its full range (n = number of positions).

Duplicates: an event is a duplicate only when its sequence number `s` is not greater than the last one applied for that control id (see Control events). The companion must **not** compare values: after the game changes state on its own (a signal self-cancels, the gearbox goes back to P) the phone legitimately sends the same value again. Repeated `latching`/`switch`/`radio` events with new sequence numbers are applied again; the built-in profiles use idempotent setters so that is harmless.

Joysticks: the phone's two-axis `joystick` control never appears on the wire. It is sent as two spring-centred bipolar `slider` controls with ids `<id>.x` (left-right) and `<id>.y` (forward-back, forward = +1), in `profile.sync` and in every `input`, so the companion needs no joystick support.

Route change on the phone: when a held control (momentary or slider) changes route (Bluetooth HID came up or went down) the phone first sends the rest value on the old route, then continues on the new one, so no axis or button is left set behind. Discrete controls (latching, switch, radio) are not parked: their rest value is a real position and sending it would be a tap, so they simply continue on the new route.

### Which channel carries which event

- **UDP** carries only continuous or held inputs: `momentary` and `slider`.
- **WebSocket** carries every discrete, state-changing event: `latching`, `switch`, `radio` and `rotary` (and therefore every macro or toggle bound to them), as `{"v":1,"type":"input","s":..,"id":..,"val":..}`. TCP delivery means one lost packet can never desync the game.

## Control events (phone to companion, UDP 47801)

One JSON object per datagram:

```json
{"v":1,"k":"<session>","s":1042,"id":"horn","val":1}
```

- `k`: session key from `welcome`; datagrams with an unknown key, or from a different IP than the WebSocket that owns the key, are dropped.
- `s`: sequence number, increasing per phone session. The companion drops an event whose `s` is not greater than the last one it applied **for the same control id**. The counter and the companion's per-id last-seen table both reset with each new session key from `welcome`. UDP and WebSocket `input` events share one counter.
- `val`: number.
- Reliability: while a momentary control is held, and for 2 s after any slider moves, the phone re-sends the current value every 250 ms. Each release (value 0) is sent 3 times, 30 ms apart. The companion auto-releases any held `hold`/`vjoy_button`/`xinput_button` input from a momentary control that has had no update for 1000 ms (sliders keep their last value).
- Fallback: if UDP cannot be used, the phone sends momentary and slider events over the WebSocket as `input` too. The companion accepts any kind on either channel.

## Game state and feedback (companion to phone, WebSocket)

```json
{"v":1,"type":"state","source":"beamng","values":{"lights.low":true,"esc.active":false,"gear":"D","speed":23.4}}
```

- `source`: `beamng` (mod), `outgauge` (telemetry fallback), or `companion`.
- `values` is a partial update; the phone merges it into its current state. A full snapshot is sent right after a profile sync and whenever the vehicle changes, with `"full":true`.
- Values are booleans, numbers or short strings.

### OutGauge mapping (verify against the current BeamNG OutGauge output)

`Gear` byte: 0 = `R`, 1 = `N`, 2+ = `1`, `2`, ... into `gear`. `Speed` (m/s) into `speed`, `RPM` into `rpm`, `Fuel` (0..1) into `fuel`. `ShowLights` bitmask (DL_* flags): `DL_FULLBEAM` (0x0002) into `lights.high`, `DL_HANDBRAKE` (0x0004) into `parkingbrake`, `DL_TC` (0x0010) into `tcs.active`, `DL_SIGNAL_L` (0x0020) into `signal.left`, `DL_SIGNAL_R` (0x0040) into `signal.right`, `DL_SIGNAL_L` and `DL_SIGNAL_R` both on into `hazard`, `DL_ABS` (0x0400) into `abs.active`. OutGauge has no availability info, so it sends no `has.*` keys.

### State keys

Indicator keys (boolean unless stated):

`ignition`, `engine.running`, `lights.low`, `lights.high`, `lights.fog`, `lights.light_bar`, `signal.left`, `signal.right`, `hazard`, `horn`, `parkingbrake`, `esc.active`, `tcs.active`, `abs.active`, `cruise.active`, `cruise.speed` (number, m/s), `diff.front.locked`, `diff.center.locked`, `diff.rear.locked`, `awd.active`, `range.low`, `gearbox.mode` (string: `P`,`R`,`N`,`D`,`S`,`M`), `gear` (string), `speed` (number, m/s), `rpm` (number), `fuel` (0..1), `game.paused`, `game.slowmo`, `camera.mode` (string), `winch.active`.

BeamNG notes from the mod: `esc.active` / `tcs.active` mean the system is enabled. BeamNG has one combined ESC/TC mode, so `tcs.toggle` = `esc.toggle`, and the setters use tracked state (on at spawn). Extra keys: `esc.intervening`, `tcs.intervening`, `ignition.level` (0-3). `abs.active` = ABS intervening. `lights.low` is true for low or high beam. `winch.active` is never reported by BeamNG (no stock winch).

Availability keys (`has.*`, boolean): the mod reports which features the current vehicle has, for example `has.diff.front`, `has.diff.center`, `has.diff.rear`, `has.awd`, `has.range`, `has.esc`, `has.tcs`, `has.cruise`, `has.fog`, `has.light_bar`, `has.gearbox.modes`. A control with `visibleWhen:"has.diff.front"` is hidden or dimmed when that key is false, and shown when it is true or unknown.

## BeamNG mod link (companion <-> mod, TCP 127.0.0.1:47803, one JSON object per line)

The mod is the TCP **client** (BeamNG Lua ships LuaSocket). It reconnects every 3 s while the companion is not reachable.

Mod to companion:

```json
{"v":1,"type":"hello","role":"beamng","modVersion":"1.0.0","gameVersion":"0.3x"}
{"v":1,"type":"state","values":{...},"full":false}
{"v":1,"type":"commands","list":["lights.low.toggle","signal.left.toggle", "..."]}
{"v":1,"type":"bindings.result","ok":true,"message":"Bound 18 actions","written":true,"count":18,"unknown":[],"device":"vjoy1","busy":false,"alreadyBound":false}
```

Companion to mod:

```json
{"v":1,"type":"command","command":"lights.low.toggle","value":1}
{"v":1,"type":"bindings.apply","device":"vjoy1","bindings":[{"beamngAction":"toggle_headlights","button":5}]}
{"v":1,"type":"state.request"}
```

`bindings.result` fields: `ok` (true only once BeamNG's input system shows the bindings loaded), `message` (always shown to the user when `ok` is false), `written` (the inputmap file was saved), `count` (bindings written), `unknown` (action names this game version does not have), `device`, `busy` (a previous apply is still being confirmed; retry in a second), `alreadyBound` (the same pairs were already live in the game before this apply, from an earlier apply or the user's own bindings; the file is still written and `written` is true).

`bindings.apply`: BeamNG keys joystick input maps by `vidpid` (lowercase PID+VID hex, e.g. vJoy = `bead1234`). GE Lua has no device-list API, so the **companion adds `"vidpid"`** to every `bindings.apply` it forwards: it reads the VID/PID of the vJoy device (`device:"vjoy1"`) or of the paired "GameCtl Pad" Bluetooth HID device (`device:"hid"`) from Windows. Without it the mod falls back to `bead1234` for `vjoy1` and answers `ok:false` for `hid`. Protocol button N maps to BeamNG `button{N-1}`. The mod writes `settings/inputmaps/<vidpid>_gamecontroller.json` in the user folder and reloads input; users can still rebind in Options > Controls.

```json
{"v":1,"type":"bindings.apply","device":"hid","vidpid":"c0de1209","bindings":[{"beamngAction":"toggle_headlights","button":5}]}
```

BeamNG input action names usable in `beamngAction`: `toggle_headlights toggle_foglights toggle_lightbar_signal toggle_left_signal toggle_right_signal toggle_hazard_signal horn activateStarterMotor toggleShifterMode shiftUp shiftDown toggleESCMode toggleDiffMode toggle4WDStatus toggleRangeStatus parkingbrake parkingbrake_toggle switch_camera_next toggleCamera look_back center_camera camera_1..camera_10 recover_vehicle recover_vehicle_alt loadHome saveHome reset_physics reset_all_physics reload_vehicle toggle_slow_motion slower_motion faster_motion pause`.

The companion relays mod `state` to the phone with `source:"beamng"`, relays the `commands` list to the phone as `{"v":1,"type":"beamng.commands","list":[...]}`, and relays `bindings.result` as-is.

Phone to companion, to ask the mod to write in-game bindings for the vJoy buttons used by the active profile:

```json
{"v":1,"type":"beamng.bindings.apply","device":"vjoy1","bindings":[{"beamngAction":"toggle_headlights","button":5}]}
```

### BeamNG command names

`<feature>.toggle` flips state once per event it receives (the phone sends it on the rising edge from a `momentary` control and on every change from a `latching` one, see Duplicates above); `<feature>` with value 1/0 sets state idempotently (setters exist for every toggle feature below); plain names for one-shots. The mod owns the exact list and reports it in `commands`. Minimum set used by the built-in profile:

`action.<beamngActionName>` (value 1 = press, 0 = release; runs any BeamNG input action by name, passes the value through), `ignition.toggle`, `starter` (value 1 = crank, 0 = stop), `lights.low.toggle`, `lights.high.toggle`, `lights.fog.toggle`, `lights.light_bar.toggle`, `signal.left.toggle`, `signal.right.toggle`, `hazard.toggle`, `horn` (value 1/0), `parkingbrake.toggle`, `esc.toggle`, `tcs.toggle`, `diff.front.toggle`, `diff.center.toggle`, `diff.rear.toggle`, `awd.toggle`, `range.toggle`, `gearbox.mode` (value = index into P,R,N,D,S,M), `gear.up`, `gear.down`, `cruise.toggle`, `cruise.set`, `cruise.up`, `cruise.down`, `camera.next`, `camera.prev`, `recover` (value 1/0, hold to rewind), `reset`, `reset.full`, `slowmo.toggle`, `slowmo.faster`, `slowmo.slower`, `pause.toggle`.

## Key names

Letters `A`-`Z`; digits `D0`-`D9`; `F1`-`F24`; `ESC TAB CAPSLOCK SPACE ENTER BACKSPACE`; `LSHIFT RSHIFT LCTRL RCTRL LALT RALT LWIN RWIN APPS`; `UP DOWN LEFT RIGHT INSERT DELETE HOME END PAGEUP PAGEDOWN`; `MINUS EQUALS LBRACKET RBRACKET SEMICOLON APOSTROPHE GRAVE BACKSLASH COMMA PERIOD SLASH`; `NUMPAD0`-`NUMPAD9 NUMPAD_ADD NUMPAD_SUBTRACT NUMPAD_MULTIPLY NUMPAD_DIVIDE NUMPAD_DECIMAL NUMPAD_ENTER NUMLOCK`; `PRINTSCREEN SCROLLLOCK PAUSE`.

Extended (need `KEYEVENTF_EXTENDEDKEY`): `RCTRL RALT LWIN RWIN APPS UP DOWN LEFT RIGHT INSERT DELETE HOME END PAGEUP PAGEDOWN NUMPAD_DIVIDE NUMPAD_ENTER PRINTSCREEN`.

## Error codes

`version_mismatch`, `bad_pin`, `bad_token`, `rate_limited`, `not_paired`, `bad_message`, `unknown_control`, `no_vjoy`, `no_beamng`, `config_write_failed` (the companion could not save its config during pairing or Forget; the PIN stays valid, nothing was persisted, try again after fixing the config path).

## Profile file format (import / export)

A profile is exported as one JSON file, `<name>.gcprofile.json`, with `"schema": 1`. It is the Android `Profile` model serialized with kotlinx.serialization (`classDiscriminator = "type"` for actions). See `examples/profile-example.json`. Importers ignore unknown fields and fill missing ones with defaults.
