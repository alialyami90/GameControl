-- Game Controller: vehicle Lua side.
-- Runs inside the player vehicle's Lua VM. Reads electrics, powertrain and controllers,
-- sends changed state to the game engine side (gameControllerLink), and applies commands
-- that need the vehicle VM. Loaded by gameControllerLink into the player vehicle.

local M = {}

local SEND_INTERVAL = 0.1     -- seconds between state checks
local HEARTBEAT_INTERVAL = 1  -- seconds between keep-alive messages when nothing changed
local CRUISE_STEP = 5 / 3.6   -- cruise up/down step, m/s (5 km/h)

local active = false
local sendTimer = 0
local heartbeatTimer = 0
local lastSent = {}
local fullRequested = true

-- powertrain features found on this vehicle, filled by scanVehicle()
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
local function geAction(actionName, value)
  toGE(string.format("if gameControllerLink then gameControllerLink.runAction(%q, %d) end", actionName, value or 1))
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

-- ============= VEHICLE SCAN =============

local function scanVehicle()
  features = { diff = {}, awd = nil, range = nil, esc = nil, gearbox = nil, hasEngine = false }
  local devices = (powertrain and powertrain.getDevices and powertrain.getDevices()) or {}
  for name, device in pairs(devices) do
    local dtype = string.lower(tostring(device.type or ""))
    local mode = device.mode
    local modes = listModes(device)
    if dtype == "combustionengine" or dtype == "electricmotor" then
      features.hasEngine = true
    end
    if name == "gearbox" or dtype:find("gearbox") then
      features.gearbox = features.gearbox or device
    end
    if type(mode) == "string" and device.setMode then
      if dtype == "rangebox" or mode == "low" or mode == "high" or contains(modes, "low") then
        features.range = features.range or { device = device }
      elseif mode == "connected" or mode == "disconnected" or contains(modes, "disconnected") then
        features.awd = features.awd or { device = device }
      elseif dtype:find("differential") or mode == "locked" or mode == "open" or mode == "lsd" or mode == "viscous" or contains(modes, "locked") then
        local loc = diffLocation(name)
        if loc and not features.diff[loc] then
          local unlocked = mode ~= "locked" and mode or nil
          if not unlocked and modes then
            for _, m in ipairs(modes) do if m ~= "locked" then unlocked = m break end end
          end
          features.diff[loc] = { device = device, modes = modes, unlocked = unlocked or "open" }
        end
      end
    end
  end
  -- the ESC controller (also does traction control), found by its getCurrentConfigData export
  if controller and controller.getAllControllers then
    for _, c in pairs(controller.getAllControllers()) do
      if type(c) == "table" and type(c.getCurrentConfigData) == "function" and type(c.toggleESCMode) == "function" then
        features.esc = c
        break
      end
    end
  end
  features.gearboxModes = false
  if features.gearbox then
    local gtype = string.lower(tostring(features.gearbox.type or ""))
    features.gearboxModes = gtype:find("automatic") or gtype:find("dct") or gtype:find("cvt") or false
    features.gearboxModes = features.gearboxModes and true or false
  elseif features.hasEngine and electrics.values.gear and type(electrics.values.gear) == "string" then
    features.gearboxModes = true -- electric vehicles report P/R/N/D without a gearbox device
  end
end

local function escEnabled()
  if not features or not features.esc then return nil end
  local ok, cfg = pcall(features.esc.getCurrentConfigData)
  if ok and type(cfg) == "table" then return cfg.escEnabled == true, cfg.name end
  return nil
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
  s["engine.running"] = truthy(e.engineRunning)

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

  local escOn, escMode = escEnabled()
  s["has.esc"] = features.esc ~= nil
  s["has.tcs"] = features.esc ~= nil
  if escOn ~= nil then
    s["esc.active"] = escOn
    s["tcs.active"] = escOn
    s["esc.mode"] = escMode and tostring(escMode) or nil
  end
  s["esc.intervening"] = truthy(e.escActive)
  s["tcs.intervening"] = truthy(e.tcsActive)
  s["abs.active"] = truthy(e.absActive)

  s["cruise.active"] = truthy(e.cruiseControlActive)
  if type(e.cruiseControlTarget) == "number" then s["cruise.speed"] = round(e.cruiseControlTarget, 0.1) end
  s["has.cruise"] = features.hasEngine

  for _, loc in ipairs({ "front", "center", "rear" }) do
    local d = features.diff[loc]
    s["has.diff." .. loc] = d ~= nil
    if d then s["diff." .. loc .. ".locked"] = d.device.mode == "locked" end
  end
  s["has.awd"] = features.awd ~= nil
  if features.awd then s["awd.active"] = features.awd.device.mode == "connected" end
  s["has.range"] = features.range ~= nil
  if features.range then s["range.low"] = features.range.device.mode == "low" end

  s["has.gearbox.modes"] = features.gearboxModes
  s["has.fog"] = e.fog ~= nil
  s["has.light_bar"] = e.lightbar ~= nil

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
    return
  end
  local delta, changed = {}, false
  for k, v in pairs(s) do
    if lastSent[k] ~= v then delta[k] = v; changed = true end
  end
  lastSent = s
  if changed then
    sendState(delta, false)
    heartbeatTimer = 0
  end
end

-- ============= COMMANDS =============

local function setLightsState(n)
  if electrics.setLightsState then electrics.setLightsState(n) end
end

-- current value and setter for each on/off feature handled in vehicle Lua
local features_onoff = {
  ["ignition"] = {
    get = function() return (tonumber(electrics.values.ignitionLevel) or 0) >= 2 end,
    set = function(on) electrics.setIgnitionLevel(on and 2 or 0) end,
  },
  ["lights.low"] = {
    get = function() return (tonumber(electrics.values.lights_state) or 0) >= 1 end,
    set = function(on) setLightsState(on and math.max(1, tonumber(electrics.values.lights_state) or 0) or 0) end,
  },
  ["lights.high"] = {
    get = function() return (tonumber(electrics.values.lights_state) or 0) >= 2 end,
    set = function(on) setLightsState(on and 2 or 1) end,
  },
  ["lights.fog"] = {
    get = function() return truthy(electrics.values.fog) end,
    set = function(on) electrics.set_fog_lights(on and 1 or 0) end,
  },
  ["lights.light_bar"] = {
    get = function() return truthy(electrics.values.lightbar) end,
    set = function(on) electrics.set_lightbar_signal(on and 1 or 0) end,
  },
  ["hazard"] = {
    get = function()
      local e = electrics.values
      return truthy(e.hazard_enabled) or (truthy(e.signal_left_input) and truthy(e.signal_right_input))
    end,
    set = function(on) electrics.set_warn_signal(on and 1 or 0) end,
  },
  ["parkingbrake"] = {
    get = function()
      local pb = electrics.values.parkingbrake_input
      if pb == nil then pb = electrics.values.parkingbrake end
      return truthy(pb)
    end,
    set = function(on) input.event("parkingbrake", on and 1 or 0, FILTER_DIRECT) end,
  },
  ["cruise"] = {
    get = function() return truthy(electrics.values.cruiseControlActive) end,
    set = function(on)
      local cc = extensions.cruiseControl
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
      d.device:setMode(on and "locked" or d.unlocked)
    end,
  }
end
features_onoff["diff.front"] = diffFeature("front")
features_onoff["diff.center"] = diffFeature("center")
features_onoff["diff.rear"] = diffFeature("rear")

features_onoff["awd"] = {
  get = function()
    if not (features and features.awd) then return nil end
    return features.awd.device.mode == "connected"
  end,
  set = function(on) features.awd.device:setMode(on and "connected" or "disconnected") end,
  fallbackAction = "toggle4WDStatus",
}
features_onoff["range"] = {
  get = function()
    if not (features and features.range) then return nil end
    return features.range.device.mode == "low"
  end,
  set = function(on) features.range.device:setMode(on and "low" or "high") end,
  fallbackAction = "toggleRangeStatus",
}

-- index into P,R,N,D,S,M as sent by the phone
local GEARBOX_MODES = { [0] = "P", "R", "N", "D", "S", "M" }
-- shiftToGearIndex values for automatic-style gearboxes (same table BeamMP uses)
local AUTO_GEAR_INDEX = { P = 1, R = -1, N = 0, D = 2, S = 3, M = 6 }
local MANUAL_GEAR_INDEX = { R = -1, N = 0, D = 1, S = 1, M = 1 }

local function setGearboxMode(index)
  local mode = GEARBOX_MODES[math.floor(tonumber(index) or -1)]
  if not mode then return end
  if not features then scanVehicle() end
  local mc = controller.mainController
  if features.gearboxModes and mc and mc.shiftToGearIndex then
    if electrics.values.gearboxMode == "arcade" and mc.setGearboxMode then try(mc.setGearboxMode, "realistic") end
    try(mc.shiftToGearIndex, AUTO_GEAR_INDEX[mode])
  elseif features.gearbox and features.gearbox.setGearIndex and MANUAL_GEAR_INDEX[mode] then
    try(features.gearbox.setGearIndex, features.gearbox, MANUAL_GEAR_INDEX[mode])
  end
end

local function shift(up)
  local mc = controller.mainController
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
  local level = tonumber(electrics.values.ignitionLevel) or 0
  if down then
    if level < 3 then electrics.setIgnitionLevel(3) end
  elseif level == 3 then
    electrics.setIgnitionLevel(2)
  end
end

local oneShots = {
  ["starter"] = function(v) starter(v >= 0.5) end,
  ["horn"] = function(v) electrics.horn(v >= 0.5) end,
  ["gearbox.mode"] = function(v) setGearboxMode(v) end,
  ["gear.up"] = function(v) if v >= 0.5 then shift(true) end end,
  ["gear.down"] = function(v) if v >= 0.5 then shift(false) end end,
  ["cruise.set"] = function(v)
    if v < 0.5 then return end
    local cc = extensions.cruiseControl
    if cc then try(cc.holdCurrentSpeed); try(cc.setEnabled, true) end
  end,
  ["cruise.up"] = function(v)
    if v < 0.5 then return end
    local cc = extensions.cruiseControl
    if cc and not try(cc.changeSpeed, CRUISE_STEP) then
      try(cc.setSpeed, (tonumber(electrics.values.cruiseControlTarget) or 0) + CRUISE_STEP)
    end
  end,
  ["cruise.down"] = function(v)
    if v < 0.5 then return end
    local cc = extensions.cruiseControl
    if cc and not try(cc.changeSpeed, -CRUISE_STEP) then
      try(cc.setSpeed, math.max(0, (tonumber(electrics.values.cruiseControlTarget) or 0) - CRUISE_STEP))
    end
  end,
  ["recover"] = function(v)
    if not recovery then return end
    if v >= 0.5 then try(recovery.startRecovering) else try(recovery.stopRecovering) end
  end,
  ["reset"] = function(v) if v >= 0.5 then obj:requestReset(RESET_PHYSICS) end end,
}

-- Entry point from gameControllerLink. mode is "toggle", "set" or "once".
local function command(name, mode, value)
  value = tonumber(value) or 1
  if not features then scanVehicle() end
  local f = features_onoff[name]
  if f then
    local ok, current = pcall(f.get)
    if not ok then current = nil end
    if current == nil then
      -- the feature can't be read on this vehicle; a toggle can still go through the game's own action
      if mode == "toggle" and f.fallbackAction then geAction(f.fallbackAction, 1) end
      return
    end
    local want
    if mode == "toggle" then want = not current else want = value >= 0.5 end
    if want ~= current then try(f.set, want) end
  elseif oneShots[name] then
    try(oneShots[name], value)
  else
    log('W', 'gameController', 'unknown vehicle command ' .. tostring(name))
  end
  sendTimer = SEND_INTERVAL -- report the result on the next frame
end

-- ============= HOOKS =============

local function updateGFX(dt)
  if not active then return end
  sendTimer = sendTimer + dt
  heartbeatTimer = heartbeatTimer + dt
  if sendTimer < SEND_INTERVAL and not fullRequested then return end
  sendTimer = 0
  local force = fullRequested
  fullRequested = false
  local ok, err = pcall(checkState, force)
  if not ok then log('E', 'gameController', 'state check failed: ' .. tostring(err)) end
  if heartbeatTimer >= HEARTBEAT_INTERVAL then
    heartbeatTimer = 0
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
