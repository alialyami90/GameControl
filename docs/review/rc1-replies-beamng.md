# RC1 replies: BeamNG mod

Status:
- Synced. `luajit tests/run.lua` gives **172 passed, 0 failed**, the same in 5 runs in a row (the previous run had 150 checks).
- `dist/gamecontroller.zip` has been rebuilt with `sh build.sh` and has 13 entries, including `mod_info/`.
- The zip name, the extension names (`gameControllerLink`, `gameControllerVehicle`), the console log tag `gameController` and the inputmap file name `<vidpid>_gamecontroller.json` are unchanged, so existing installs and bindings keep working.

## Version 1.0.0 everywhere

| Where | Value |
|---|---|
| `MOD_VERSION` in `gameControllerLink.lua` | `1.0.0` |
| Console line when the mod loads | `GameCtl mod 1.0.0 loaded, looking for the companion on 127.0.0.1:47803` |
| `hello.modVersion` sent to the companion | `1.0.0`. The companion logs `BeamNG mod 1.0.0 (game <version>)` and shows "Mod 1.0.0 connected" on its BeamNG row (`MainViewModel.cs`). |
| `mod_info/gamecontroller/info.json` `version_string` | `1.0.0` |

## Mod info for BeamNG's mod manager

New file `gamecontroller/mod_info/gamecontroller/info.json`, packed by `build.sh`. It has these fields:
- `title`: "GameCtl for BeamNG"
- `username` and `author`: "ALI"
- `version_string`: "1.0.0"
- `tag_line`: one line
- `message`: the description, including that the mod connects only to 127.0.0.1:47803 and does nothing without the companion
- `tagid`: "gamecontroller"
- `tags`: controller, phone, android, utility

## Product name: GameCtl

All text a player sees now says GameCtl. The Lua file header comments were renamed too.

**Final strings for SMOKE_TEST.md** (quoted exactly; EXPECTED.md and the README are updated):

| What | Exact text |
|---|---|
| Mods list title | `GameCtl for BeamNG` |
| Console, mod loaded | `GameCtl mod 1.0.0 loaded, looking for the companion on 127.0.0.1:47803` |
| Console, linked | `connected to the companion` (unchanged) |
| On-screen message, linked | `Connected to the GameCtl companion` |
| Console, companion closed | `companion connection closed: closed` (unchanged) |
| On-screen message, companion closed | `GameCtl companion disconnected` |
| Title on the on-screen messages | `GameCtl` |
| Console error, no LuaSocket | `LuaSocket is not available in this game version, the GameCtl link is disabled` |
| Bluetooth pad name in bindings messages | `GameCtl Pad` (it was already this) |

The console filter word is still `gameController`, because that is the log tag.

## Robustness pass

- **Companion absent.**
  - `socket.tcp()` failing is now handled with one warning. Before, it hit a nil call.
  - Refused connects, retries every 3 s and reconnecting were already tested. No E lines.
- **Level unload.**
  - New `onClientEndMission` hook. It forgets held commands, the vehicle, the cached actions and the pending attach, because every vehicle goes away with the level and there is nothing left to release.
  - Found and fixed a related gap: when vehicle Lua reports for a car the link has no snapshot of (after a level change, or getting back into a car you left), the link now asks that car for a full snapshot. Before, it merged deltas onto an empty state, so most lamps stayed unknown until something changed.
  - Tested: the link stays connected, vehicle values are dropped, and the next car reports a full snapshot.
- **A vehicle whose Lua never answers.**
  - Its vehicle part was being reloaded every 0.5 s, and a load error would have filled the console.
  - It is now retried every 5 s (`VEHICLE_TIMEOUT`).
  - Tested: 1 load in 2 s, 2 loads after 6 s.
- **Vehicle without powertrain or controllers** (trailer, prop).
  - `shift` and `setGearboxMode` now check that `controller` exists, and `horn` checks for `electrics.horn`. Every command path was already inside a `pcall`, so these were caught warnings before; now they don't happen.
  - Tested in a separate vehicle VM with no `powertrain`, no `controller` and no `extensions`:
    - the state collects, with every `has.*` left out;
    - all 8 one-shots and 5 toggles run without a Lua error;
    - no E lines.
- The suite's final check, "no errors logged", still passes over the whole run.

## Known limits (README)

The README has a new **Known limits** section:
- B-S1: a held game-engine `action.*` releases in the car you switched to. Horn, Start and Recover release in their own car.
- Trailers and props report lamps and speed only, and their controls stay visible.
- Stock BeamNG has no winch. The duplicate line about this in "Notes on specific features" was removed.
- The mod connects only to the companion on the same PC.

## Open reviewer comments

The mod has none open. Every B and B-S item from rounds 1-4 is answered in `round1`…`round4-replies-beamng.md`. B-S1 is accepted as a known limit and is now documented in the README.
