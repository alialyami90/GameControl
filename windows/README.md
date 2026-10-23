# GameCtl Companion for Windows

The PC half of GameCtl. The Android app sends control events over Wi-Fi; the companion turns them into input any Windows game can see, and sends game state back so the phone's indicators light from the game. It is game-agnostic. BeamNG.drive is fully supported through its mod and OutGauge telemetry.

Protocol: [`../protocol/PROTOCOL.md`](../protocol/PROTOCOL.md) (v1). The companion implements it as written.

## What it does

| Output | How | Needs |
|---|---|---|
| Keyboard | `SendInput` with hardware scancodes (`KEYEVENTF_SCANCODE`, extended flag where needed), so DirectInput and raw-input games read it | nothing |
| vJoy joystick | `vJoyInterface.dll` from the installed driver, loaded at runtime; up to 16 devices, 128 buttons, 8 axes | [vJoy 2.2.x, BrunnerInnovation fork](https://github.com/BrunnerInnovation/vJoy/releases), device 1 enabled in Configure vJoy |
| Xbox pad (optional) | ViGEmBus via `Nefarius.ViGEm.Client`, plugged in on first use | [ViGEmBus 1.22](https://github.com/nefarius/ViGEmBus/releases) (archived upstream, still works) |
| BeamNG commands and state | TCP `127.0.0.1:47803`, the mod connects as client | `beamng-mod/dist/gamecontroller.zip` in BeamNG's `mods` folder |
| Telemetry fallback | BeamNG OutGauge on UDP 4444, relayed at most 10 times a second, paused while the mod is connected | Options > Other > OutGauge, IP 127.0.0.1, port 4444 |

Phones find the PC by mDNS (`_gamectl._tcp`), by UDP broadcast on 47802, or by typed address. Pairing uses the 6-digit PIN shown in the window (5 wrong tries lock pairing for 60 s; the PIN changes after each pairing). Paired phones get a long-lived token; only its SHA-256 is stored. Several phones can be connected at once, each with its own bindings and held inputs. A phone disconnecting releases only what it held; closing the companion releases everything.

## Build, test, publish

Requires the .NET 8 SDK (any later SDK also works through `global.json` roll-forward). From this folder:

```sh
dotnet build                      # everything; builds on Windows, Linux and macOS
dotnet test                       # 93 unit + loopback integration tests
dotnet publish src/Companion.App -c Release -r win-x64 -o publish/win-x64
```

The publish step makes one self-contained `GameCtlCompanion.exe` (no .NET install needed on the gaming PC). `dotnet publish -c Release -r win-x64` on the whole solution also works; tests and the screenshot tool are excluded from publishing.

## Projects

| Path | What |
|---|---|
| `src/Companion.Core` | Everything platform-neutral: protocol parsing, key table, binding engine, pairing, WebSocket/UDP/discovery/mDNS servers, BeamNG link, OutGauge parser, `CompanionHost` |
| `src/Companion.Windows` | Windows outputs and helpers: `SendInputKeyboard`, `VJoyOutput`, `ViGEmOutput`, HID VID/PID lookup for BeamNG input maps, firewall rule, start with Windows, elevation check, `DnsServiceRegister` fallback |
| `src/Companion.App` | The tray app (Avalonia 11). Same visual world as the phone app (Race Front-End tokens from DESIGN.md, Barlow and italic Barlow Condensed) |
| `src/Companion.Cli` | Console host. Same engine, no UI. Prints every injection with `--dry-run` (always on outside Windows) |
| `tools/PhoneSim` | Phone and mod simulator: `discover`, `demo --pin`, `token`, `mod` |
| `tools/UiSnap` | Renders every page headlessly with a simulated phone and mod, and saves PNGs (`docs/screenshots`) |
| `tests/Companion.Tests` | xUnit tests |

WinForms and WPF were ruled out because they cannot be built off Windows; Avalonia builds everywhere and still ships as a native Windows tray app.

## Try it without a phone

```sh
dotnet run --project src/Companion.Cli -- --pin 123456          # terminal 1
dotnet run --project tools/PhoneSim -- mod                       # terminal 2 (fake BeamNG mod, optional)
dotnet run --project tools/PhoneSim -- demo --pin 123456         # terminal 3
```

The demo pairs, syncs a profile with every control kind, presses everything over UDP and WebSocket, and lets one press go silent to show the 1 s auto-release. On Windows without `--dry-run` it injects for real, so focus Notepad first.

Screenshots for design review: `dotnet run --project tools/UiSnap -- docs/screenshots`.

## Behaviour worth knowing

- Bindings marked `"route":"hid"` are never injected (the phone sends those over Bluetooth HID).
- Duplicates are dropped by sequence number only. A `latching`, `switch` or `radio` event with a new sequence number is applied again even when its value repeats, because the game may have changed state on its own. `momentary` holds (keys, buttons and held BeamNG commands such as `horn` or `action.*`) auto-release after 1 s without updates; sliders keep their value.
- When a phone's session ends (closed, 8 s without a message, replaced, forgotten), everything it held is released: keys and buttons go up, held BeamNG commands get value 0, bipolar axes return to centre and unipolar axes to 0. BeamNG setters such as `lights.low` are left as they are.
- A profile re-sync releases only controls whose binding was removed or changed; an unchanged latched hold stays down.
- vJoy devices are listed when free, and a device is acquired only the first time a binding uses it.
- WebSocket upgrades that carry an `Origin` header (that is, from a browser) are refused with 403.
- Holds are reference-counted, so two controls or two phones holding the same key release it only when both let go.
- A tap on a key that is still mid-tap waits for it (about 20 ms gap), so two quick toggles or two rotary detents reach the game as two presses.
- `PAUSE` is sent as scancode E0 45, which DirectInput reports as `DIK_PAUSE`; the real E1 1D 45 sequence cannot be expressed with `SendInput`.
- BeamNG `bindings.apply` from the phone is forwarded with `vidpid`: vJoy is `bead1234`; `hid` is looked up from the paired "GameCtl Pad" HID device.
- UDP events are accepted only with a live session key and only from the IP of the WebSocket that owns it.

## Troubleshooting

- **The phone cannot find the PC.** Allow the companion through Windows Firewall (the Status page offers it; it adds one rule for private networks). Check the PC and phone are on the same network and the network is set to Private. Type the address shown under the PIN as a last resort.
- **Keys do nothing in one game.** If the game runs as administrator, Windows blocks input from apps that are not. The companion warns about this; start it with Run as administrator.
- **vJoy shows Needs setup.** Install vJoy, open Configure vJoy, enable device 1. The companion picks it up within ten seconds.
- **SimHub (or another dashboard) also wants OutGauge.** Only one program can listen on UDP 4444. Either give the companion another port in Settings and point BeamNG there, or use SimHub's UDP forwarding to pass OutGauge on to the companion's port. With the BeamNG mod connected the companion does not need OutGauge at all.
- **A key seems stuck.** Status > Release all input, or the tray menu item of the same name.

Settings live in `%APPDATA%\GameController\companion.json` (the folder name is kept from the betas so their settings and pairings carry over). The log is `%APPDATA%\GameController\companion.log`, with one older file `companion.log.1`; each is kept under 1 MB. If the settings file cannot be read it is kept as `companion.json.unreadable-<date>` and defaults are used.
