# RC2 replies: BeamNG mod

Status:
- Synced. `luajit tests/run.lua` gives **175 passed, 0 failed**, the same on 5 runs in a row.
- `dist/gamecontroller.zip` has been rebuilt. Every file in it is byte-identical to the source (checked with `cmp`).
- B-6 (the PROTOCOL.md title) is with the Lead developer.

## B-1: socket warning repeats every 3 s
Fixed. `startConnect` sets `socketWarned` the first time `socket.tcp()` fails and never logs that line again. It keeps retrying every 3 s without writing anything, which matches EXPECTED.md.

## B-2: new line missing from EXPECTED.md
Fixed. "Lines that mean something is wrong" now lists:

`could not create a socket: <detail>` (shown as `W`, at most once per game start; the mod keeps retrying quietly every 3 seconds)

## B-3: first retry after a switch, spawn or reload waited 5 s
Fixed.
- **Before:** after the first unanswered load, every retry waited 5 s.
- **Now:** `attachVehicle` counts unanswered loads into the same car (`attachTries`). The first retry still comes after 0.5 s. Only after that does a car that still doesn't answer move to retries every `VEHICLE_TIMEOUT` (5 s).
- New test: switch to a car whose Lua doesn't run. Within 0.7 s it has been loaded twice.
- Silent-car test updated:
  - in the first 2 s: a load, then one retry after 0.5 s;
  - for the next 3 s: no retries;
  - then a retry every 5 s.
- Both tests fail if the retry delay goes back to 5 s for the first retry. I checked by mutating the code.

## B-4: the level-unload test didn't depend on the hook
Fixed.
- The test now holds `action.horn_test` across `onClientEndMission`.
- After the next car reports, pressing that action again must run only the press (`hornLog == "1"`).
- Without the hook, the stale hold makes the link release first (`"01"`).
- With `M.onClientEndMission` stubbed to an empty function, this check fails (174 passed, 1 failed). I checked by mutating the code.

## B-5: README said "one vehicle VM"
Fixed. README.md:58 now says the tests simulate:
- the game engine VM;
- the companion;
- four vehicles:
  - a full car with its own vehicle VM;
  - a trailer with its own vehicle VM and no powertrain or controllers;
  - two cars that only record what is sent to them, one for switches and one whose Lua never answers.

## B-7: tagid in info.json
Dropped. I couldn't confirm in game that it's harmless. `info.json` now has:
- `title`
- `prefix_title`
- `tag_line`
- `username`
- `author`
- `version_string`
- `tags`
- `message`

The zip's copy has no `tagid`.

## RC3

Status:
- Synced. `luajit tests/run.lua` gives **182 passed, 0 failed**, the same on 5 runs in a row.
- `dist/gamecontroller.zip` has been rebuilt. Every file in it is byte-identical to the source.

### B-3: quick retry lost after the second Ctrl+R
Fixed.
- `onVehicleState` now sets `attachTries = 0` next to `vehicleId = id` when the car answers. Every reload of a car that has answered starts again with the 0.5 s first retry.
- New test, run twice in a row on the same car:
  - quieten the car's Lua;
  - Ctrl+R (`onVehicleSpawned`);
  - drop the first load;
  - expect a retry within 0.7 s;
  - expect the car to report again.
- Without the reset, round 2 fails (181 passed, 1 failed). I checked by mutating the code.

### B-8: no test for the once-only socket warning
Added.
- The test makes `socket.tcp()` fail and drops the companion link, then runs 10 s (three or more retries).
- It expects:
  - exactly one `could not create a socket` line;
  - the link still idle;
  - a reconnect once sockets work again.
- With `socketWarned = true` removed, it fails with 3 warnings (181 passed, 1 failed). I checked by mutating the code.
