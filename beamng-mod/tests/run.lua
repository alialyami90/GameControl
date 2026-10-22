-- Offline tests for the BeamNG mod. Run from beamng-mod/: luajit tests/run.lua
-- Needs LuaJIT and LuaSocket. Simulates the game engine VM and one vehicle VM with mocks of the
-- BeamNG APIs the mod uses, and plays the companion with a real TCP server on 127.0.0.1:47803.

package.path = "./tests/?.lua;" .. package.path
local json = require("json")
local socket = require("socket")

local MOD = "./gamecontroller/lua/"
local failures, passes = 0, 0

local function check(cond, name)
  if cond then passes = passes + 1 else failures = failures + 1; print("FAIL: " .. name) end
end

-- ============= SHARED MOCKS =============

local logs = {}
local function mockLog(level, tag, msg) table.insert(logs, level .. " " .. tostring(msg)) end

local ge, ve -- environments
local geQueue, veQueue = {}, {}

local function newEnv()
  local env = {}
  setmetatable(env, { __index = _G })
  env.log = mockLog
  env.jsonEncode = json.encode
  env.jsonDecode = json.decode
  return env
end

local function loadInto(env, path)
  local chunk = assert(loadfile(path))
  setfenv(chunk, env)
  return chunk()
end

local function runQueued(queue, env)
  local n = 0
  while #queue > 0 do
    local code = table.remove(queue, 1)
    local fn = assert(loadstring(code))
    setfenv(fn, env)
    local ok, err = pcall(fn)
    if not ok then error("queued code failed: " .. tostring(err) .. "\n" .. code) end
    n = n + 1
  end
  return n
end

-- ============= VEHICLE VM =============

ve = newEnv()
ve.FILTER_DIRECT = 2
ve.RESET_PHYSICS = 1
local calls = {}
local function record(name) return function(...) table.insert(calls, { name, ... }) end end

local escConfigs = { { name = "Regular", escEnabled = true }, { name = "Off", escEnabled = false }, { name = "Sport", escEnabled = true } }
local escKey = 1
local escCtrl = {
  typeName = "esc", name = "esc",
  getCurrentConfigData = function() return escConfigs[escKey] end,
  toggleESCMode = function() escKey = escKey % #escConfigs + 1 end,
}

local diffF = { type = "differential", mode = "open", setMode = function(self, m) self.mode = m end }
local diffR = { type = "differential", mode = "lsd", setMode = function(self, m) self.mode = m end }
local transfer = { type = "shaft", mode = "connected", setMode = function(self, m) self.mode = m end }
local rangebox = { type = "rangeBox", mode = "high", setMode = function(self, m) self.mode = m end }
local gearbox = { type = "automaticGearbox" }

local e = {
  ignitionLevel = 2, engineRunning = 1, lights_state = 0, fog = 0, lightbar = 0,
  signal_left_input = 0, signal_right_input = 0, horn = 0, parkingbrake_input = 0,
  escActive = false, tcsActive = false, absActive = false, cruiseControlActive = false, cruiseControlTarget = 0,
  gear = "P", wheelspeed = 0, rpm = 800, fuel = 0.75,
}
ve.electrics = {
  values = e,
  setLightsState = function(n) e.lights_state = n end,
  set_fog_lights = function(v) e.fog = v end,
  set_lightbar_signal = function(v) e.lightbar = v end,
  set_warn_signal = function(v) e.signal_left_input = v; e.signal_right_input = v end,
  toggle_left_signal = function() e.signal_left_input = 1 - e.signal_left_input; e.signal_right_input = 0 end,
  toggle_right_signal = function() e.signal_right_input = 1 - e.signal_right_input; e.signal_left_input = 0 end,
  horn = function(on) e.horn = on and 1 or 0 end,
  setIgnitionLevel = function(l) e.ignitionLevel = l end,
}
ve.input = { event = function(name, v, filter) if name == "parkingbrake" then e.parkingbrake_input = v end end }
ve.powertrain = { getDevices = function()
  return { differential_F = diffF, differential_R = diffR, transfercase = transfer, rangebox = rangebox, gearbox = gearbox,
    mainEngine = { type = "combustionEngine" } }
end }
ve.controller = {
  getAllControllers = function() return { esc = escCtrl, other = { typeName = "shiftLogic" } } end,
  mainController = {
    shiftToGearIndex = function(i) table.insert(calls, { "shiftToGearIndex", i }) end,
    shiftUpOnDown = record("shiftUpOnDown"),
  },
}
ve.extensions = {
  cruiseControl = {
    holdCurrentSpeed = function() e.cruiseControlTarget = e.wheelspeed end,
    setEnabled = function(on) e.cruiseControlActive = on end,
    changeSpeed = function(d) e.cruiseControlTarget = e.cruiseControlTarget + d end,
  },
  load = function(name)
    if name == "gameControllerVehicle" then
      ve.gameControllerVehicle = loadInto(ve, MOD .. "vehicle/extensions/gameControllerVehicle.lua")
      ve.gameControllerVehicle.onExtensionLoaded()
    end
  end,
}
ve.recovery = { startRecovering = record("startRecovering"), stopRecovering = record("stopRecovering") }
ve.obj = {
  getID = function() return 7 end,
  queueGameEngineLua = function(_, code) table.insert(geQueue, code) end,
  requestReset = function(_, kind) table.insert(calls, { "requestReset", kind }) end,
}

-- ============= GAME ENGINE VM =============

ge = newEnv()
local files = {}
local paused, simSpeed = false, 1
local playerId = 7
local veh = {
  getID = function() return 7 end,
  getJBeamFilename = function() return "pickup" end,
  queueLuaCommand = function(_, code) table.insert(veQueue, code) end,
}
ge.be = {
  getPlayerVehicle = function(_, i) return playerId >= 0 and veh or nil end,
  getPlayerVehicleID = function(_, i) return playerId end,
  getObjectByID = function(_, id) return id == 7 and veh or nil end,
  queueAllObjectLua = function(_, code) table.insert(veQueue, code) end,
}
ge.simTimeAuthority = {
  getPause = function() return paused end,
  pause = function(p) paused = p end,
  getReal = function() return simSpeed end,
}
local camIndex = 0
ge.core_camera = {
  setVehicleCameraByIndexOffset = function(p, d) camIndex = camIndex + d end,
  getActiveCamName = function() return "cam" .. camIndex end,
}
local reloaded = {}
ge.core_input_bindings = { onFileChanged = function(p) table.insert(reloaded, p) end }
ge.guihooks = { trigger = function() end }
ge.beamng_versionb = "0.38.3.0"
local actionFiles = {
  ["/lua/ge/extensions/core/input/actions/vehicle.json"] = {
    toggle_slow_motion = { cat = "x", order = 1, onDown = "testHits.slowmo = (testHits.slowmo or 0) + 1" },
    shiftUp = { ctx = "vlua", onDown = "electrics.values.gear = 'D'" },
    recover_vehicle = { ctx = "vlua", onDown = "recovery.startRecovering()", onUp = "recovery.stopRecovering()" },
    reset_physics = { onDown = "testHits.reset = VEHICLEID" },
    horn_test = { onChange = "testHits.hornValue = VALUE" },
  },
}
ge.testHits = {}
-- in the game, action code runs in the global GE environment; here that is the ge table
ge.loadstring = function(code) local fn, err = loadstring(code); if fn then setfenv(fn, ge) end; return fn, err end
ge.FS = {
  findFiles = function(_, dir, pattern)
    local out = {}
    for path in pairs(actionFiles) do if path:sub(1, #dir) == dir then table.insert(out, path) end end
    return out
  end,
  fileExists = function(_, p) return files[p] ~= nil end,
  removeFile = function(_, p) files[p] = nil end,
}
ge.jsonReadFile = function(p) return actionFiles[p] end
ge.writeFile = function(p, data) files[p] = data; return true end

local link = loadInto(ge, MOD .. "ge/extensions/gameControllerLink.lua")
ge.gameControllerLink = link

-- ============= COMPANION (TCP SERVER) =============

local server = assert(socket.bind("127.0.0.1", 47803))
server:settimeout(0)
local client
local inbox = {}

local function pump(frames)
  for _ = 1, frames or 1 do
    link.onUpdate(0.05)
    runQueued(veQueue, ve)
    if ve.gameControllerVehicle then ve.gameControllerVehicle.updateGFX(0.05) end
    runQueued(geQueue, ge)
    if not client then
      client = server:accept()
      if client then client:settimeout(0) end
    end
    if client then
      while true do
        local line, err = client:receive("*l")
        if not line then break end
        table.insert(inbox, json.decode(line))
      end
    end
    socket.sleep(0.005)
  end
end

local function sendToMod(t)
  t.v = 1
  client:send(json.encode(t) .. "\n")
end

local function lastOfType(t)
  for i = #inbox, 1, -1 do if inbox[i].type == t then return inbox[i] end end
end

local function merged()
  local s = {}
  for _, m in ipairs(inbox) do
    if m.type == "state" then
      if m.full then s = {} end
      for k, v in pairs(m.values) do s[k] = v end
    end
  end
  return s
end

local function command(name, value)
  sendToMod({ type = "command", command = name, value = value })
  pump(6)
end

-- ============= TESTS =============

-- connection and handshake (retry interval is 3 s, so run 70 frames of 50 ms)
pump(70)
check(client ~= nil, "mod connects to the companion")
check(link._status() == "connected", "status is connected")
local hello = lastOfType("hello")
check(hello and hello.role == "beamng" and hello.v == 1 and hello.gameVersion == "0.38.3.0", "hello message")
local cmds = lastOfType("commands")
local cmdSet = {}
for _, c in ipairs(cmds and cmds.list or {}) do cmdSet[c] = true end
for _, required in ipairs({ "ignition.toggle", "starter", "lights.low.toggle", "lights.high.toggle", "lights.fog.toggle",
  "lights.light_bar.toggle", "signal.left.toggle", "signal.right.toggle", "hazard.toggle", "horn", "parkingbrake.toggle",
  "esc.toggle", "tcs.toggle", "diff.front.toggle", "diff.center.toggle", "diff.rear.toggle", "awd.toggle", "range.toggle",
  "gearbox.mode", "gear.up", "gear.down", "cruise.toggle", "cruise.set", "cruise.up", "cruise.down", "camera.next",
  "camera.prev", "recover", "reset", "reset.full", "slowmo.toggle", "slowmo.faster", "slowmo.slower", "pause.toggle",
  "lights.low", "hazard", "esc", "diff.front", "parkingbrake", "awd", "range", "cruise", "slowmo", "pause", "ignition" }) do
  check(cmdSet[required], "commands list has " .. required)
end

pump(10)
local full
for _, m in ipairs(inbox) do if m.type == "state" and m.full then full = m end end
check(full ~= nil, "full snapshot sent after connect")
local s = merged()
check(s["ignition"] == true and s["engine.running"] == true, "ignition and engine state")
check(s["has.diff.front"] == true and s["has.diff.rear"] == true and s["has.diff.center"] == false, "diff availability")
check(s["has.awd"] == true and s["awd.active"] == true, "awd detected")
check(s["has.range"] == true and s["range.low"] == false, "range detected")
check(s["has.esc"] == true and s["esc.active"] == true and s["esc.mode"] == "Regular", "esc state")
check(s["gear"] == "P" and s["gearbox.mode"] == "P" and s["has.gearbox.modes"] == true, "gear state")
check(s["fuel"] == 0.75 and s["rpm"] == 800, "fuel and rpm")
check(s["game.paused"] == false and s["game.slowmo"] == false and s["camera.mode"] == "cam0", "game state")

-- lights
command("lights.low.toggle", 1)
check(merged()["lights.low"] == true and e.lights_state == 1, "low beam on")
command("lights.high.toggle", 1)
check(merged()["lights.high"] == true and e.lights_state == 2, "high beam on")
command("lights.high", 0)
check(merged()["lights.high"] == false and merged()["lights.low"] == true, "high beam off keeps low")
command("lights.low", 0)
check(e.lights_state == 0 and merged()["lights.low"] == false, "lights off")
command("lights.low", 0)
check(e.lights_state == 0, "setter is idempotent")
command("lights.fog.toggle", 1)
check(merged()["lights.fog"] == true, "fog on")
command("lights.light_bar", 1)
check(merged()["lights.light_bar"] == true, "light bar on")

-- signals and hazard
command("signal.left.toggle", 1)
check(merged()["signal.left"] == true and merged()["signal.right"] == false, "left signal")
command("hazard", 1)
check(merged()["hazard"] == true and merged()["signal.left"] == false, "hazard on")
command("signal.right", 1)
check(merged()["hazard"] == false and merged()["signal.right"] == true, "right signal cancels hazard")
command("signal.right.toggle", 1)
check(merged()["signal.right"] == false, "right signal off")

-- horn, parking brake, ignition, starter
command("horn", 1)
check(merged()["horn"] == true, "horn down")
command("horn", 0)
check(merged()["horn"] == false, "horn up")
command("parkingbrake.toggle", 1)
check(merged()["parkingbrake"] == true, "parking brake on")
command("parkingbrake", 0)
check(merged()["parkingbrake"] == false, "parking brake off")
command("ignition.toggle", 1)
check(merged()["ignition"] == false and e.ignitionLevel == 0, "ignition off")
command("starter", 1)
check(e.ignitionLevel == 3, "starter cranks")
command("starter", 0)
check(e.ignitionLevel == 2 and merged()["ignition"] == true, "starter released")

-- ESC / TC
command("esc.toggle", 1)
check(merged()["esc.active"] == false and merged()["esc.mode"] == "Off", "esc off")
command("esc", 1)
check(merged()["esc.active"] == true, "esc setter on")
command("esc", 1)
check(merged()["esc.active"] == true, "esc setter idempotent")
command("tcs", 0)
check(merged()["tcs.active"] == false, "tcs setter off")

-- diffs, awd, range
command("diff.front.toggle", 1)
check(diffF.mode == "locked" and merged()["diff.front.locked"] == true, "front diff locked")
command("diff.front.toggle", 1)
check(diffF.mode == "open", "front diff back to open")
command("diff.rear", 1)
check(diffR.mode == "locked", "rear diff locked")
command("diff.rear", 0)
check(diffR.mode == "lsd", "rear diff back to lsd")
command("awd.toggle", 1)
check(transfer.mode == "disconnected" and merged()["awd.active"] == false, "awd off")
command("range", 1)
check(rangebox.mode == "low" and merged()["range.low"] == true, "low range")

-- gearbox
calls = {}
command("gearbox.mode", 3)
check(calls[1] and calls[1][1] == "shiftToGearIndex" and calls[1][2] == 2, "gearbox mode D")
command("gear.up", 1)
check(merged()["gear"] == "D", "gear up through the game's action")

-- gearbox mode strings
for gearValue, expected in pairs({ ["L"] = "S", ["M3"] = "M", [3] = "M", ["-1"] = "R", ["0"] = "N", ["D"] = "D" }) do
  e.gear = gearValue
  pump(8)
  check(merged()["gearbox.mode"] == expected, "gearbox.mode for gear " .. tostring(gearValue) .. " got " .. tostring(merged()["gearbox.mode"]))
end
e.gear = "D"
pump(4)

-- cruise
e.wheelspeed = 20
command("cruise.toggle", 1)
check(merged()["cruise.active"] == true and merged()["cruise.speed"] == 20, "cruise on at current speed")
command("cruise.up", 1)
check(math.abs(merged()["cruise.speed"] - 21.4) < 0.01, "cruise up 5 km/h")
command("cruise", 0)
check(merged()["cruise.active"] == false, "cruise off")

-- camera, recover, reset
command("camera.next", 1)
check(merged()["camera.mode"] == "cam1", "camera next")
command("camera.prev", 1)
check(merged()["camera.mode"] == "cam0", "camera prev")
calls = {}
command("recover", 1)
command("recover", 0)
check(calls[1] and calls[1][1] == "startRecovering" and calls[2] and calls[2][1] == "stopRecovering", "recover hold")
command("reset", 1)
check(ge.testHits.reset == 7, "reset runs the action with VEHICLEID")

-- game
command("pause.toggle", 1)
check(paused == true and merged()["game.paused"] == true, "pause")
command("pause", 0)
check(paused == false, "unpause")
command("slowmo.toggle", 1)
check(ge.testHits.slowmo == 1, "slowmo toggle action")
command("slowmo", 0)
check(ge.testHits.slowmo == 1, "slowmo setter does nothing when already normal speed")
command("action.horn_test", 1)
check(ge.testHits.hornValue == 1, "generic action press")
command("action.horn_test", 0)
check(ge.testHits.hornValue == 0, "generic action release")

-- bindings
sendToMod({ type = "bindings.apply", device = "vjoy1", vidpid = "BEAD1234",
  bindings = { { beamngAction = "toggle_slow_motion", button = 5 }, { beamngAction = "missing_action", button = 1 }, { beamngAction = "bad name", button = 2 } } })
pump(4)
local res = lastOfType("bindings.result")
local written = files["/settings/inputmaps/bead1234_gamecontroller.json"]
check(res and res.ok == true and res.count == 2, "bindings result ok")
check(written ~= nil, "bindings file written")
local map = written and json.decode(written)
check(map and map.vidpid == "bead1234" and map.bindings[1].control == "button4" and map.bindings[1].action == "toggle_slow_motion", "bindings content")
check(reloaded[#reloaded] == "/settings/inputmaps/bead1234_gamecontroller.json", "input bindings reloaded")
check(res and res.unknown and res.unknown[1] == "missing_action", "unknown actions reported")
sendToMod({ type = "bindings.apply", device = "hid", bindings = { { beamngAction = "horn", button = 1 } } })
pump(4)
res = lastOfType("bindings.result")
check(res and res.ok == false, "hid without vidpid fails")
sendToMod({ type = "bindings.apply", device = "vjoy1", bindings = {} })
pump(4)
check(files["/settings/inputmaps/bead1234_gamecontroller.json"] == nil, "empty bindings remove the file")

-- state.request gives a new full snapshot
local before = #inbox
sendToMod({ type = "state.request" })
pump(6)
local gotFull = false
for i = before + 1, #inbox do if inbox[i].type == "state" and inbox[i].full then gotFull = true end end
check(gotFull, "state.request answered with a full snapshot")

-- leaving the vehicle drops vehicle keys
playerId = -1
link.onVehicleSwitched(7, -1)
pump(4)
s = merged()
check(s["gear"] == nil and s["game.paused"] == false, "no vehicle: only game keys")
playerId = 7
link.onVehicleSwitched(-1, 7)
pump(10)
check(merged()["gear"] ~= nil, "vehicle back: vehicle keys reported again")

-- version mismatch closes
client:send(json.encode({ v = 2, type = "command", command = "horn", value = 1 }) .. "\n")
pump(4)
local err = lastOfType("error")
check(err and err.code == "version_mismatch", "version mismatch error")
check(link._status() ~= "connected", "connection closed on version mismatch")
client:close(); client = nil

-- reconnects after 3 s
pump(80)
check(client ~= nil and link._status() == "connected", "reconnects")

-- no errors logged by the mod
local errors = {}
for _, l in ipairs(logs) do if l:sub(1, 1) == "E" then table.insert(errors, l) end end
check(#errors == 0, "no errors logged: " .. table.concat(errors, " | "))

print(string.format("%d passed, %d failed", passes, failures))
os.exit(failures == 0 and 0 or 1)
