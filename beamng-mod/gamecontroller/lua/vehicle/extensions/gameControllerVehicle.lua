-- GameCtl: vehicle Lua side.
-- Runs inside the player vehicle's Lua VM. Reads electrics, powertrain and controllers,
-- sends changed state to the game engine side (gameControllerLink), and applies commands
-- that need the vehicle VM. Loaded by gameControllerLink into the player vehicle.
--
-- Conservative rule: a has.* key is only sent when the mod could actually tell. Unknown
-- availability is left out, so the phone shows the control.

local M = {}

local SEND_INTERVAL = 0.1     -- seconds between state checks
local SEND_FRAMES = 6         -- or this many frames, so state still flows while paused (dt is 0)
local HEARTBEAT_INTERVAL = 1  -- seconds between keep-alive messages when nothing changed
local HEARTBEAT_FRAMES = 30   -- 1.5 s at 20 fps, well inside the game engine side's 5 s timeout
local CRUISE_STEP = 5 / 3.6   -- cruise up/down step, m/s (5 km/h)

local active = false
local sendTimer, sendFrames = 0, 0
local heartbeatTimer, heartbeatFrames = 0, 0
local lastSent = {}
local fullRequested = true

-- features found on this vehicle, filled by scanVehicle()
local features = nil

-- ============= HELPERS =============

local function truthy(v)
  if type(v) == "boolean" then return v end
  if type(v) == "number" then return v > 0.5 end
  return false
end

local function round(x, step)
  if type(x) ~= "number" then return x end
  return math.floor(x / step + 0.5) * step
end

local function try(f, ...)
  if type(f) ~= "function" then return false end
  local ok, err = pcall(f, ...)
  if not ok then log('W', 'gameController', 'call failed: ' .. tostring(err)) end
  return ok
end

local function toGE(code)
  obj:queueGameEngineLua(code)
end

-- ask the game engine side to run a BeamNG input action instead (fallback route)
-- fallbackFeature: if the action is missing or fails, GE asks back for a direct toggle of this feature
local function geAction(actionName, value, fallbackFeature)
  toGE(string.format("if gameControllerLink then gameControllerLink.runAction(%q, %d, %s) end",
    actionName, value or 1, fallbackFeature and string.format("%q", fallbackFeature) or "nil"))
end

local function listModes(device)
  local modes = device.availableModes or device.modes
  if type(modes) ~= "table" then return nil end
  local list = {}
  for k, v in pairs(modes) do
    if type(v) == "string" then table.insert(list, v)
    elseif type(k) == "string" then table.insert(list, k) end
  end
  table.sort(list)
  return #list > 0 and list or nil
end

local function contains(list, value)
  if not list then return false end
  for _, v in ipairs(list) do if v == value then return true end end
  return false
end

-- where a differential sits, from its device name (differential_F, rearDiff, transfercase...)
local function diffLocation(name)
  local n = string.lower(name)
  if n:find("center") or n:find("centre") or n:find("transfer") or n:find("_c$") or n:find("^c_") then return "center" end
  if n:find("front") or n:find("_f$") or n:find("^f_") or n:find("_f_") then return "front" end
  if n:find("rear") or n:find("_r$") or n:find("^r_") or n:find("_r_") then return "rear" end
  return nil
end

-- change a powertrain device mode through powertrain.setDeviceMode when the game has it,
-- so the game's UI and electrics follow; otherwise on the device itself
local function setDeviceMode(entry, mode)
  if powertrain and type(powertrain.setDeviceMode) == "function" then
    local ok = pcall(powertrain.setDeviceMode, entry.name, mode)
    if ok then return end
  end
  entry.device:setMode(mode)
end

-- does the vehicle data mention this electric (props, glow maps)? Bounded search.
local function vehicleDataMentions(pattern)
  if not (v and type(v.data) == "table") then return nil end
  local seen, budget = {}, 20000
  local function walk(t, depth)
    if budget <= 0 or depth > 6 or seen[t] then return false end
    seen[t] = true
    for k, val in pairs(t) do
      budget = budget - 1
      if type(k) == "string" and k:lower():find(pattern) then return true end
      if type(val) == "string" and val:lower():find(pattern) then return true end
      if type(val) == "table" and walk(val, depth + 1) then return true end
    end
    return false
  end
  local found = false
  for _, section in ipairs({ "props", "glowMap", "flexbodies" }) do
    if type(v.data[section]) == "table" and walk(v.data[section], 0) then found = true break end
  end
  return found
end

-- ============= VEHICLE SCAN =============

-- A real ESC controller exports toggleESCMode. A stand-in that answers every key with a function
-- (like getControllerSafe's dummy) is recognised by also answering a key no controller has.
local function isRealEscController(c)
  if type(c) ~= "table" then return false end
  local ok, real = pcall(function()
    return type(c.toggleESCMode) == "function" and type(c.__gameControllerProbe_noSuchKey) ~= "function"
  end)
  return ok and real
end

local function findEscController()
  if not controller then return nil end
  local candidates = {}
  if type(controller.getController) == "function" then
    local ok, c = pcall(controller.getController, "esc")
    if ok then table.insert(candidates, c) end
  end
  if type(controller.getAllControllers) == "function" then
    local ok, all = pcall(controller.getAllControllers)
    if ok and type(all) == "table" then
      for _, c in pairs(all) do table.insert(candidates, c) end
    end
  end
  for _, c in ipairs(candidates) do
    if isRealEscController(c) then return c end
  end
  return nil
end

local function hasDriveModes()
  if not (controller and type(controller.getController) == "function") then return false end
  local ok, c = pcall(controller.getController, "driveModes")
  return ok and type(c) == "table"
end

local function loadCruiseControl()
  local cc = rawget(_G, "cruiseControl")
  if not cc and extensions and type(extensions.load) == "function" then
    pcall(extensions.load, "cruiseControl")
    cc = rawget(_G, "cruiseControl")
  end
  if not cc and extensions then
    local ok, ext = pcall(function() return extensions.cruiseControl end)
    if ok then cc = ext end
  end
  if type(cc) == "table" and type(cc.setEnabled) == "function" then return cc end
  return nil
end

local function scanVehicle()
  features = { diff = {}, diffDevices = 0, awd = nil, range = nil, esc = nil, gearbox = nil, hasEngine = false }
  local devices = (powertrain and powertrain.getDevices and powertrain.getDevices()) or {}
  for name, device in pairs(devices) do
    local dtype = string.lower(tostring(device.type or ""))
    local mode = device.mode
    local modes = listModes(device)
    if dtype == "combustionengine" or dtype == "electricmotor" then features.hasEngine = true end
    if name == "gearbox" or dtype:find("gearbox") then features.gearbox = features.gearbox or device end
    if dtype:find("differential") then features.diffDevices = features.diffDevices + 1 end
    if type(mode) == "string" and device.setMode then
      local entry = { name = name, device = device, modes = modes }
      if dtype == "rangebox" or mode == "low" or mode == "high" or contains(modes, "low") then
        features.range = features.range or entry
      elseif mode == "disconnected" or contains(modes, "disconnected") or (dtype ~= "differential" and mode == "connected") then
        -- 4WD: any engaged mode counts as on; remember it so "on" restores the same one
        entry.engaged = mode ~= "disconnected" and mode or nil
        if not entry.engaged and modes then
          for _, m in ipairs(modes) do if m ~= "disconnected" then entry.engaged = m break end end
        end
        entry.engaged = entry.engaged or "connected"
        features.awd = features.awd or entry
      elseif dtype:find("differential") or mode == "locked" or mode == "open" or mode == "lsd" or mode == "viscous" or contains(modes, "locked") then
        local loc = diffLocation(name)
        if loc and not features.diff[loc] then
          local unlocked = mode ~= "locked" and mode or nil
          if not unlocked and modes then
            for _, m in ipairs(modes) do if m ~= "locked" then unlocked = m break end end
          end
          entry.unlocked = unlocked or "open"
          features.diff[loc] = entry
        end
      end
    end
  end
  features.esc = findEscController()
  features.driveModes = hasDriveModes()
  features.cruise = loadCruiseControl()
  features.fog = vehicleDataMentions("fog")
  features.lightbar = vehicleDataMentions("lightbar")
  features.gearboxModes = nil
  if features.gearbox then
    local gtype = string.lower(tostring(features.gearbox.type or ""))
    features.gearboxModes = (gtype:find("automatic") or gtype:find("dct") or gtype:find("cvt")) and true or false
  elseif features.hasEngine and type(electrics.values.gear) == "string" then
    features.gearboxModes = true -- electric vehicles report P/R/N/D without a gearbox device
  end
end

local function escEnabled()
  local esc = features and features.esc
  if not esc or type(esc.getCurrentConfigData) ~= "function" then return nil end
  local ok, cfg = pcall(esc.getCurrentConfigData)
  if ok and type(cfg) == "table" and type(cfg.escEnabled) == "boolean" then return cfg.escEnabled, cfg.name end
  return nil
end

local function cruiseState()
  local e = electrics.values
  local on, target = e.cruiseControlActive, e.cruiseControlTarget
  if (on == nil or target == nil) and features and features.cruise and type(features.cruise.getConfiguration) == "function" then
    local ok, cfg = pcall(features.cruise.getConfiguration)
    if ok and type(cfg) == "table" then
      if on == nil then on = cfg.isEnabled end
      if target == nil then target = cfg.targetSpeed end
    end
  end
  return on, target
end

-- ============= STATE =============

-- P/R/N/D/S/M as the phone expects. Manual gearboxes report a number (as a number or a string),
-- automatics a letter plus an optional number ("D", "M3"); low gears ("L", "1", "2") count as S.
local function gearboxMode(gear)
  local n = tonumber(gear)
  if n then
    if n < 0 then return "R" elseif n == 0 then return "N" else return "M" end
  end
  if type(gear) == "string" and #gear > 0 then
    local c = string.upper(string.sub(gear, 1, 1))
    if c == "P" or c == "R" or c == "N" or c == "D" or c == "S" or c == "M" then return c end
    return "S"
  end
  return nil
end

local function collectState()
  if not features then scanVehicle() end
  local e = electrics.values
  local s = {}

  local ign = e.ignitionLevel
  if type(ign) == "number" then
    s["ignition.level"] = ign
    s["ignition"] = ign >= 2
  elseif e.ignition ~= nil then
    s["ignition"] = truthy(e.ignition)
  end
  if e.engineRunning ~= nil then s["engine.running"] = truthy(e.engineRunning) end

  local lightsState = tonumber(e.lights_state) or 0
  s["lights.low"] = lightsState >= 1
  s["lights.high"] = lightsState >= 2
  s["lights.fog"] = truthy(e.fog)
  s["lights.light_bar"] = truthy(e.lightbar)

  local left = truthy(e.signal_left_input)
  local right = truthy(e.signal_right_input)
  local hazard = truthy(e.hazard_enabled) or (left and right)
  s["hazard"] = hazard
  s["signal.left"] = left and not hazard
  s["signal.right"] = right and not hazard

  s["horn"] = truthy(e.horn)
  local pb = e.parkingbrake_input
  if pb == nil then pb = e.parkingbrake end
  s["parkingbrake"] = truthy(pb)

  if features.esc then
    s["has.esc"] = true
    s["has.tcs"] = true
    local escOn, escMode = escEnabled()
    if escOn ~= nil then
      s["esc.active"] = escOn
      s["tcs.active"] = escOn
      if escMode then s["esc.mode"] = tostring(escMode) end
    end
  end
  s["esc.intervening"] = truthy(e.escActive)
  s["tcs.intervening"] = truthy(e.tcsActive)
  s["abs.active"] = truthy(e.absActive)

  local cruiseOn, cruiseTarget = cruiseState()
  if features.cruise then s["has.cruise"] = true end
  if cruiseOn ~= nil then s["cruise.active"] = truthy(cruiseOn) end
  if type(cruiseTarget) == "number" then s["cruise.speed"] = round(cruiseTarget, 0.1) end

  -- diff, 4WD and range availability is only sent when the powertrain scan found the car's diffs
  local scanWorked = features.diffDevices > 0
  for _, loc in ipairs({ "front", "center", "rear" }) do
    local d = features.diff[loc]
    if d then
      s["has.diff." .. loc] = true
      s["diff." .. loc .. ".locked"] = d.device.mode == "locked"
    elseif scanWorked then
      s["has.diff." .. loc] = false
    end
  end
  if features.awd then
    s["has.awd"] = true
    s["awd.active"] = features.awd.device.mode ~= "disconnected"
  elseif scanWorked then
    s["has.awd"] = false
  end
  if features.range then
    s["has.range"] = true
    s["range.low"] = features.range.device.mode == "low"
  elseif scanWorked then
    s["has.range"] = false
  end

  if features.gearboxModes ~= nil then s["has.gearbox.modes"] = features.gearboxModes end
  if features.fog then s["has.fog"] = true end
  if features.lightbar then s["has.light_bar"] = true end

  local gear = e.gear
  if gear ~= nil then s["gear"] = tostring(gear) end
  s["gearbox.mode"] = gearboxMode(gear)
  s["speed"] = round(tonumber(e.wheelspeed) or tonumber(e.airspeed) or 0, 0.1)
  s["rpm"] = round(tonumber(e.rpm) or 0, 10)
  if type(e.fuel) == "number" then s["fuel"] = round(e.fuel, 0.01) end
  return s
end

local function sendState(values, full)
  toGE(string.format("if gameControllerLink then gameControllerLink.onVehicleState(%d, %q, %s) end",
    obj:getID(), jsonEncode(values), tostring(full == true)))
end

local function checkState(force)
  local s = collectState()
  if force then
    lastSent = s
    sendState(s, true)
    return true
  end
  local delta, changed = {}, false
  for k, val in pairs(s) do
    if lastSent[k] ~= val then delta[k] = val; changed = true end
  end
  lastSent = s
  if changed then sendState(delta, false) end
  return changed
end

-- ============= COMMANDS =============

local function setLightsState(n)
  if electrics.setLightsState then electrics.setLightsState(n) end
end

-- current value and setter for each on/off feature handled in vehicle Lua.
-- fallbackAction: BeamNG action used for a toggle when the state can't be read on this vehicle.
local features_onoff = {
  ["ignition"] = {
    get = function() return (tonumber(electrics.values.ignitionLevel) or 0) >= 2 end,
    set = function(on) electrics.setIgnitionLevel(on and 2 or 0) end,
  },
  ["lights.low"] = {
    get = function() return (tonumber(electrics.values.lights_state) or 0) >= 1 end,
    set = function(on) setLightsState(on and math.max(1, tonumber(electrics.values.lights_state) or 0) or 0) end,
    fallbackAction = "toggle_headlights",
  },
  ["lights.high"] = {
    get = function() return (tonumber(electrics.values.lights_state) or 0) >= 2 end,
    set = function(on) setLightsState(on and 2 or 1) end,
  },
  ["lights.fog"] = {
    get = function() return truthy(electrics.values.fog) end,
    set = function(on) electrics.set_fog_lights(on and 1 or 0) end,
    fallbackAction = "toggle_foglights",
  },
  ["lights.light_bar"] = {
    get = function() return truthy(electrics.values.lightbar) end,
    set = function(on) electrics.set_lightbar_signal(on and 1 or 0) end,
    fallbackAction = "toggle_lightbar_signal",
  },
  ["hazard"] = {
    get = function()
      local e = electrics.values
      return truthy(e.hazard_enabled) or (truthy(e.signal_left_input) and truthy(e.signal_right_input))
    end,
    set = function(on) electrics.set_warn_signal(on and 1 or 0) end,
    fallbackAction = "toggle_hazard_signal",
  },
  ["parkingbrake"] = {
    get = function()
      local pb = electrics.values.parkingbrake_input
      if pb == nil then pb = electrics.values.parkingbrake end
      return truthy(pb)
    end,
    set = function(on) input.event("parkingbrake", on and 1 or 0, FILTER_DIRECT) end,
    fallbackAction = "parkingbrake_toggle",
  },
  ["cruise"] = {
    get = function()
      local on = cruiseState()
      if on == nil then return nil end
      return truthy(on)
    end,
    set = function(on)
      local cc = features and features.cruise
      if not cc then return end
      if on then
        try(cc.holdCurrentSpeed)
        try(cc.setEnabled, true)
      else
        try(cc.setEnabled, false)
      end
    end,
  },
  ["esc"] = {
    get = function() return escEnabled() end,
    set = function(on)
      local esc = features and features.esc
      if not esc then return end
      -- step through the configurations until one matches, at most one full cycle
      for _ = 1, 8 do
        if escEnabled() == on then return end
        esc.toggleESCMode()
      end
    end,
    -- toggles go through the game's own action first (it also knows newer drive-mode systems)
    toggleAction = "toggleESCMode",
    fallbackAction = "toggleESCMode",
  },
}
features_onoff["tcs"] = features_onoff["esc"]

local function signalFeature(side)
  local own, other = "signal_" .. side .. "_input", "signal_" .. (side == "left" and "right" or "left") .. "_input"
  local toggle = side == "left" and "toggle_left_signal" or "toggle_right_signal"
  return {
    get = function()
      local e = electrics.values
      return truthy(e[own]) and not truthy(e[other]) and not truthy(e.hazard_enabled)
    end,
    set = function(on)
      local e = electrics.values
      if on then
        if truthy(e.hazard_enabled) or truthy(e[other]) then electrics.set_warn_signal(0) end
        if not truthy(electrics.values[own]) then electrics[toggle]() end
      elseif truthy(e[own]) and not truthy(e[other]) then
        electrics[toggle]()
      end
    end,
    fallbackAction = toggle,
  }
end
features_onoff["signal.left"] = signalFeature("left")
features_onoff["signal.right"] = signalFeature("right")

local function diffFeature(loc)
  return {
    get = function()
      local d = features and features.diff[loc]
      if not d then return nil end
      return d.device.mode == "locked"
    end,
    set = function(on)
      local d = features and features.diff[loc]
      if not d then return end
      if d.device.mode ~= "locked" then d.unlocked = d.device.mode end
      setDeviceMode(d, on and "locked" or d.unlocked)
    end,
    fallbackAction = "toggleDiffMode",
  }
end
features_onoff["diff.front"] = diffFeature("front")
features_onoff["diff.center"] = diffFeature("center")
features_onoff["diff.rear"] = diffFeature("rear")

features_onoff["awd"] = {
  get = function()
    if not (features and features.awd) then return nil end
    return features.awd.device.mode ~= "disconnected"
  end,
  set = function(on)
    local a = features.awd
    if a.device.mode ~= "disconnected" then a.engaged = a.device.mode end
    setDeviceMode(a, on and a.engaged or "disconnected")
  end,
  fallbackAction = "toggle4WDStatus",
}
features_onoff["range"] = {
  get = function()
    if not (features and features.range) then return nil end
    return features.range.device.mode == "low"
  end,
  set = function(on) setDeviceMode(features.range, on and "low" or "high") end,
  fallbackAction = "toggleRangeStatus",
}

-- index into P,R,N,D,S,M as sent by the phone
local GEARBOX_MODES = { [0] = "P", "R", "N", "D", "S", "M" }
-- shiftToGearIndex values for automatic-style gearboxes (same table BeamMP uses to sync gears)
local AUTO_GEAR_INDEX = { P = 1, R = -1, N = 0, D = 2, S = 3, M = 6 }
-- manual and sequential gearboxes: gear index (D, S and M pick first gear)
local MANUAL_GEAR_INDEX = { R = -1, N = 0, D = 1, S = 1, M = 1 }

local function setGearboxMode(index)
  local mode = GEARBOX_MODES[math.floor(tonumber(index) or -1)]
  if not mode then return end
  if not features then scanVehicle() end
  local mc = controller and controller.mainController
  local target
  if features.gearboxModes then target = AUTO_GEAR_INDEX[mode] else target = MANUAL_GEAR_INDEX[mode] end
  if target == nil then return end -- P on a manual gearbox
  if mc and mc.shiftToGearIndex and try(mc.shiftToGearIndex, target) then return end
  if features.gearbox and features.gearbox.setGearIndex then
    try(features.gearbox.setGearIndex, features.gearbox, target)
  end
end

local function shift(up)
  local mc = controller and controller.mainController
  if not mc then return end
  local onDown = up and mc.shiftUpOnDown or mc.shiftDownOnDown
  local onUp = up and mc.shiftUpOnUp or mc.shiftDownOnUp
  if onDown then
    try(onDown)
    if onUp then try(onUp) end
  else
    try(up and mc.shiftUp or mc.shiftDown)
  end
end

local function starter(down)
  local mc = controller and controller.mainController
  if mc and type(mc.setStarter) == "function" and try(mc.setStarter, down) then
    if down then log('I', 'gameController', 'starter: mainController.setStarter') end
    return
  end
  -- fallback: ignition key to the start position while held
  if down then log('I', 'gameController', 'starter: ignition level fallback') end
  local level = tonumber(electrics.values.ignitionLevel) or 0
  if down then
    if level < 3 then electrics.setIgnitionLevel(3) end
  elseif level == 3 then
    electrics.setIgnitionLevel(2)
  end
end

local function changeCruise(delta)
  local cc = features and features.cruise
  if not cc then return end
  if not try(cc.changeSpeed, delta) then
    local _, target = cruiseState()
    try(cc.setSpeed, math.max(0, (tonumber(target) or 0) + delta))
  end
end

local oneShots = {
  ["starter"] = function(val) starter(val >= 0.5) end,
  ["horn"] = function(val) if electrics.horn then electrics.horn(val >= 0.5) end end,
  ["gearbox.mode"] = function(val) setGearboxMode(val) end,
  ["gear.up"] = function(val) if val >= 0.5 then shift(true) end end,
  ["gear.down"] = function(val) if val >= 0.5 then shift(false) end end,
  ["cruise.set"] = function(val)
    if val < 0.5 then return end
    local cc = features and features.cruise
    if cc then try(cc.holdCurrentSpeed); try(cc.setEnabled, true) end
  end,
  ["cruise.up"] = function(val) if val >= 0.5 then changeCruise(CRUISE_STEP) end end,
  ["cruise.down"] = function(val) if val >= 0.5 then changeCruise(-CRUISE_STEP) end end,
  ["recover"] = function(val)
    if not recovery then return end
    if val >= 0.5 then try(recovery.startRecovering) else try(recovery.stopRecovering) end
  end,
  ["reset"] = function(val) if val >= 0.5 then obj:requestReset(RESET_PHYSICS) end end,
}

-- Entry point from gameControllerLink. mode is "toggle", "set", "once", or "direct"
-- (a toggle that skips the game's action, used when that action is missing).
local function command(name, mode, value)
  value = tonumber(value) or 1
  if not features then scanVehicle() end
  local f = features_onoff[name]
  if f then
    if mode == "toggle" and f.toggleAction then
      geAction(f.toggleAction, 1, name)
      return
    end
    if mode == "direct" then mode = "toggle" end
    local ok, current = pcall(f.get)
    if not ok then current = nil end
    if current == nil then
      -- the feature can't be read on this vehicle; a toggle can still go through the game's own action
      if mode == "toggle" and f.fallbackAction then geAction(f.fallbackAction, 1) end
      return
    end
    local want
    if mode == "toggle" then want = not current else want = value >= 0.5 end
    if want ~= current and not try(f.set, want) and mode == "toggle" and f.fallbackAction then
      geAction(f.fallbackAction, 1)
    end
  elseif oneShots[name] then
    try(oneShots[name], value)
  else
    log('W', 'gameController', 'unknown vehicle command ' .. tostring(name))
  end
  sendFrames = SEND_FRAMES -- report the result on the next frame
end

-- ============= HOOKS =============

local function updateGFX(dt)
  if not active then return end
  dt = tonumber(dt) or 0
  sendTimer, sendFrames = sendTimer + dt, sendFrames + 1
  heartbeatTimer, heartbeatFrames = heartbeatTimer + dt, heartbeatFrames + 1
  if sendTimer < SEND_INTERVAL and sendFrames < SEND_FRAMES and not fullRequested then return end
  sendTimer, sendFrames = 0, 0
  local force = fullRequested
  fullRequested = false
  local ok, changed = pcall(checkState, force)
  if not ok then log('E', 'gameController', 'state check failed: ' .. tostring(changed)) end
  if ok and changed then
    heartbeatTimer, heartbeatFrames = 0, 0
  elseif heartbeatTimer >= HEARTBEAT_INTERVAL or heartbeatFrames >= HEARTBEAT_FRAMES then
    heartbeatTimer, heartbeatFrames = 0, 0
    sendState({}, false)
  end
end

local function setActive(on)
  active = on and true or false
  if active then fullRequested = true end
end

local function requestFull()
  active = true
  fullRequested = true
end

local function onReset()
  features = nil
  fullRequested = true
end

local function onExtensionLoaded()
  features = nil
  fullRequested = true
end

M.updateGFX = updateGFX
M.onReset = onReset
M.onExtensionLoaded = onExtensionLoaded
M.setActive = setActive
M.requestFull = requestFull
M.command = command

-- exposed for the offline tests
M._collectState = collectState
M._scanVehicle = scanVehicle

return M
