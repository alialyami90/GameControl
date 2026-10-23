# What the BeamNG console shows

This page lists the console lines the GameCtl mod writes, quoted exactly, for the combined smoke test (`docs/SMOKE_TEST.md`).

Open the console with the ` key (left of 1) and type `gameController` in its filter box. Every line from the mod is tagged `gameController`, with `I` (info), `W` (warning) or `E` (error) before it. The game adds a timestamp in front. The same lines are saved in `beamng.log` in the user folder.

Values in angle brackets change from run to run.

## Normal lines

| When | Line | Also on screen |
|---|---|---|
| The mod loads (game start, or the mod is enabled) | `GameCtl mod 1.0.0 loaded, looking for the companion on 127.0.0.1:47803` | none |
| The companion links (within about 3 s of both running) | `connected to the companion` | message "Connected to the GameCtl companion" |
| A car is entered, switched to, spawned or reloaded | `vehicle <car folder>: has.esc=<true/unknown> has.cruise=<…> has.fog=<…> has.light_bar=<…> has.awd=<…> has.range=<…> has.diff.front=<…> has.diff.center=<…> has.diff.rear=<…> has.gearbox.modes=<…>` | none |
| A phone command arrives | `command <name> <value>`, for example `command lights.low 1`, `command horn 1` then `command horn 0`, `command esc.toggle 1` | the game reacts |
| Start is held | `starter: mainController.setStarter` (main route) or `starter: ignition level fallback` | engine cranks |
| The companion closes | `companion connection closed: closed` | message "GameCtl companion disconnected" |
| Some game actions run (Shift up/down, Recover, Look back…) | `action failed in game engine Lua, running it in vehicle Lua (this is normal for some actions): <detail>` | none; this one is expected |
| Bind in game | none in the console | message "Bound N actions to vJoy device 1" or "Bound N actions to the phone controller". If BeamNG couldn't confirm: "Saved N bindings for … BeamNG could not confirm they are active: make sure the controller is connected, then check Options > Controls." |

While the companion isn't running, the mod tries again every 3 seconds without writing anything, so a quiet console is normal then.

## Lines that mean something is wrong (report them)

- `action failed in vehicle Lua: <detail>`
- `BeamNG action not available: <name>`
- `action failed: <detail>`
- `unknown command <name>`
- `vehicle Lua went quiet, reloading the vehicle part` (repeated)
- `state check failed: <detail>` (shown as `E`)
- `LuaSocket is not available in this game version, the GameCtl link is disabled` (shown as `E`)
- `call failed: <detail>`
- `could not create a socket: <detail>` (shown as `W`, at most once per game start; the mod keeps retrying quietly every 3 seconds)
