-- Game Controller: game engine Lua side.
-- Connects to the Windows companion over TCP (127.0.0.1:47803, newline-delimited JSON, protocol v1),
-- reports vehicle and game state, runs commands from the phone, and writes in-game bindings for
-- the vJoy / Bluetooth HID buttons the phone profile uses. See protocol/PROTOCOL.md.

local M = {}

local socketOk, socket = pcall(require, "socket")
if not socketOk then
  log('E', 'gameController', 'LuaSocket is not available in this game version, the Game Controller link is disabled: ' .. tostring(socket))
  socket = nil
end

local PROTOCOL_VERSION = 1
local MOD_VERSION = "1.0.0"
local HOST = "127.0.0.1"
local PORT = 47803
local RETRY_INTERVAL = 3      -- seconds between connection attempts
local CONNECT_TIMEOUT = 3     -- seconds to wait for the TCP handshake
local VEHICLE_TIMEOUT = 3     -- seconds (real time) without vehicle Lua messages before reloading it
local MAX_SEND_BUFFER = 1024 * 1024
local BINDINGS_SUFFIX = "_gamecontroller"
local BINDINGS_VERIFY_DELAY = 1 -- seconds after writing before checking the input system loaded them
local VJOY_VIDPID = "bead1234" -- vJoy: VID 0x1234, PID 0xBEAD, BeamNG writes PID then VID

local sock = nil
local status = "idle"         -- idle | connecting | connected
local retryTimer = RETRY_INTERVAL
local connectTimer = 0
local recvPartial = ""
local sendBuffer = ""
local pendingBindings = nil   -- bindings waiting to be confirmed as loaded
local finishBindings          -- defined with the bindings code

local state = {}              -- last known values, merged
local pending = {}            -- values changed since the last send
local pendingCount = 0
local fullPending = false     -- send a full snapshot when the vehicle answers
local vehicleId = nil         -- player vehicle that is reporting
local vehicleKeys = {}        -- keys that came from vehicle Lua, cleared on switch
local vehicleSilence = 0
local gameTimer = 0

local heldActions = {}        -- [actionName] = true while an action.* or hold command is down
local actions = nil           -- action definitions read from the game's files
local actionsVehicle = nil    -- vehicle directory the vehicle-specific actions were read for

-- ============= JSON / LOG =============

local function logI(msg) log('I', 'gameController', msg) end
local function logW(msg) log('W', 'gameController', msg) end

local function encode(t)
  return jsonEncode(t)
end

local function decode(s)
  local ok, t = pcall(jsonDecode, s)
  if ok and type(t) == "table" then return t end
  return nil
end

local function toast(kind, msg)
  if guihooks and guihooks.trigger then
    guihooks.trigger("toastrMsg", { type = kind, title = "Game Controller", msg = msg })
  end
end

-- ============= SOCKET =============

local function closeSocket()
  if sock then pcall(sock.close, sock) end
  sock = nil
  status = "idle"
  recvPartial = ""
  sendBuffer = ""
  retryTimer = 0
end

local function flush()
  if not sock or status ~= "connected" or sendBuffer == "" then return end
  local sent, err, lastIndex = sock:send(sendBuffer)
  if sent then
    sendBuffer = ""
  elseif err == "timeout" then
    sendBuffer = string.sub(sendBuffer, (lastIndex or 0) + 1)
  else
    logW("send failed: " .. tostring(err))
    closeSocket()
  end
end

local function send(msg)
  if status ~= "connected" then return end
  msg.v = PROTOCOL_VERSION
  sendBuffer = sendBuffer .. encode(msg) .. "\n"
  if #sendBuffer > MAX_SEND_BUFFER then
    logW("companion is not reading, dropping the connection")
    closeSocket()
    return
  end
  flush()
end

-- ============= ACTIONS =============
-- BeamNG input actions run with the same code the keyboard would run, so commands follow whatever
-- the installed game version does. Definitions come from the input system when it exposes them,
-- otherwise from the game's own files. Per the BeamNG docs a missing ctx means "tlua" (game engine)
-- for regular actions and "vlua" for vehicle-specific ones.

local DEPRECATED_CTX = { ui = true, ts = true, slua = true }

local function addDefs(data, into, defaultCtx)
  if type(data) ~= "table" then return end
  for name, def in pairs(data) do
    if type(name) == "string" and type(def) == "table" then
      def._ctx = def.ctx or defaultCtx
      def._ctxExplicit = def.ctx ~= nil
      into[name] = def
    end
  end
end

local function readActionFiles(dir, pattern, into, defaultCtx)
  if not (FS and FS.findFiles) then return end
  local ok, files = pcall(FS.findFiles, FS, dir, pattern, 0, true, false)
  if not ok or type(files) ~= "table" then return end
  for _, file in ipairs(files) do
    local okRead, data = pcall(jsonReadFile, file)
    if okRead then addDefs(data, into, defaultCtx) end
  end
end

local function playerVehicle()
  return be and be:getPlayerVehicle(0) or nil
end

local function vehicleDir(veh)
  if not veh then return nil end
  local ok, name = pcall(function() return veh:getJBeamFilename() end)
  if ok and type(name) == "string" and name ~= "" then return name end
  return nil
end

-- the input system's own merged list, if this game version exposes one
local function activeActionsFromGame()
  local mod = rawget(_G, "core_input_actions")
  if type(mod) ~= "table" or type(mod.getActiveActions) ~= "function" then return nil end
  local ok, list = pcall(mod.getActiveActions)
  if not ok or type(list) ~= "table" or next(list) == nil then return nil end
  return list
end

local function loadActions()
  local veh = playerVehicle()
  local dir = vehicleDir(veh)
  if actions and actionsVehicle == dir then return actions end
  local list = {}
  readActionFiles("/lua/ge/extensions/core/input/actions/", "*.json", list, "tlua")
  readActionFiles("/vehicles/common/", "input_actions*.json", list, "vlua")
  if dir then readActionFiles("/vehicles/" .. dir .. "/", "input_actions*.json", list, "vlua") end
  -- the game's merged list wins where it has an entry; its ctx is already resolved
  local fromGame = activeActionsFromGame()
  if fromGame then
    for name, def in pairs(fromGame) do
      if type(name) == "string" and type(def) == "table" then
        local known = list[name]
        local ctx = def.ctx or (known and known._ctx) or (def.vehicle and "vlua") or "tlua"
        def._ctx = ctx
        def._ctxExplicit = def.ctx ~= nil or (known and known._ctxExplicit) or false
        list[name] = def
      end
    end
  end
  actions = list
  actionsVehicle = dir
  return actions
end

local function substitute(code, value, vid)
  local subs = {
    VALUE = tostring(value), FILTERTYPE = "0", PLAYER = "0", VEHICLEID = tostring(vid or -1),
    ANGLE = "0", LOCKTYPE = "0", OSCLOCKHP = "0",
  }
  return (code:gsub("%f[%w_]([%u]+)%f[^%w_]", function(word) return subs[word] end))
end

-- returns true when the code was run (game engine) or queued (vehicle Lua)
local function runCode(ctx, code, ctxExplicit)
  local veh = playerVehicle()
  if ctx == "vlua" then
    if not veh then return false end
    veh:queueLuaCommand(code)
    return true
  elseif ctx == "bvlua" then
    be:queueAllObjectLua(code)
    return true
  end
  local fn, err = loadstring(code)
  if not fn then logW("action code failed to compile: " .. tostring(err)) return false end
  local ok, runErr = pcall(fn)
  if ok then return true end
  -- an action without an explicit ctx that fails in game engine Lua may be vehicle code: try it there once
  if not ctxExplicit and veh then
    logI("action failed in game engine Lua, running it in vehicle Lua: " .. tostring(runErr))
    veh:queueLuaCommand(code)
    return true
  end
  logW("action failed: " .. tostring(runErr))
  return false
end

local function hasAction(name)
  local def = loadActions()[name]
  return def ~= nil and not DEPRECATED_CTX[def._ctx]
end

local function vehicleDirect(feature)
  local veh = playerVehicle()
  if not veh then return end
  veh:queueLuaCommand(string.format("local m = extensions.gameControllerVehicle if m then m.command(%q, 'direct', 1) end", feature))
end

-- value 1 = press, 0 = release. Returns false if the action is missing or failed.
-- fallbackFeature (from vehicle Lua): toggle that feature directly instead when the action can't run.
local function runAction(name, value, fallbackFeature)
  local def = loadActions()[name]
  if not def or DEPRECATED_CTX[def._ctx] then
    logW("BeamNG action not available: " .. tostring(name))
    if fallbackFeature then vehicleDirect(fallbackFeature) end
    return false
  end
  value = tonumber(value) or 1
  local down = value >= 0.5
  local veh = playerVehicle()
  local vid = veh and veh:getID() or -1
  local snippets = {}
  if down and type(def.onDown) == "string" then table.insert(snippets, def.onDown) end
  if not down and type(def.onUp) == "string" then table.insert(snippets, def.onUp) end
  if type(def.onChange) == "string" then table.insert(snippets, def.onChange) end
  local ran = #snippets == 0 -- nothing to do for this edge is not a failure
  for _, code in ipairs(snippets) do
    if runCode(def._ctx, substitute(code, down and 1 or 0, vid), def._ctxExplicit) then ran = true end
  end
  if not ran and fallbackFeature then vehicleDirect(fallbackFeature) end
  return ran
end

local function tapAction(name)
  if runAction(name, 1) then runAction(name, 0) return true end
  return false
end

-- ============= STATE =============

local function setValue(key, value)
  if state[key] ~= value then
    state[key] = value
    pending[key] = value
    pendingCount = pendingCount + 1
  end
end

local function sendFull()
  fullPending = false
  pending, pendingCount = {}, 0
  send({ type = "state", values = state, full = true })
end

local function flushState()
  if pendingCount == 0 then return end
  local values = pending
  pending, pendingCount = {}, 0
  send({ type = "state", values = values, full = false })
end

local function readGameState()
  if simTimeAuthority then
    local paused = false
    if simTimeAuthority.getPause then
      local ok, p = pcall(simTimeAuthority.getPause)
      if ok then paused = p == true; setValue("game.paused", paused) end
    end
    -- while paused the sim speed reads as stopped, so keep the last slow motion value
    if not paused and simTimeAuthority.getReal then
      local ok, speed = pcall(simTimeAuthority.getReal)
      if ok and type(speed) == "number" and speed > 0 then setValue("game.slowmo", speed < 0.999) end
    end
  end
  if core_camera and core_camera.getActiveCamName then
    local ok, cam = pcall(core_camera.getActiveCamName, 0)
    if ok and cam ~= nil then setValue("camera.mode", tostring(cam)) end
  end
end

-- called from vehicle Lua with a JSON string of changed values
local function onVehicleState(id, json, full)
  if id ~= be:getPlayerVehicleID(0) then return end
  vehicleSilence = 0
  vehicleId = id
  local values = decode(json) or {}
  if full then
    for k in pairs(vehicleKeys) do state[k] = nil end
    vehicleKeys = {}
  end
  for k, v in pairs(values) do
    vehicleKeys[k] = true
    setValue(k, v)
  end
  if full or fullPending then
    readGameState()
    sendFull()
  end
end

-- load the vehicle extension into the player vehicle and ask it for a full snapshot
local function attachVehicle()
  local veh = playerVehicle()
  vehicleSilence = 0
  if not veh then
    vehicleId = nil
    for k in pairs(vehicleKeys) do state[k] = nil end
    vehicleKeys = {}
    readGameState()
    sendFull()
    return
  end
  fullPending = true
  veh:queueLuaCommand("local m = extensions.gameControllerVehicle if not m then extensions.load('gameControllerVehicle') m = extensions.gameControllerVehicle end if m then m.requestFull() end")
end

local function requestFullState()
  fullPending = true
  attachVehicle()
end

-- ============= COMMANDS =============

local function vehicleCommand(name, mode, value)
  local veh = playerVehicle()
  if not veh then return end
  veh:queueLuaCommand(string.format("local m = extensions.gameControllerVehicle if m then m.command(%q, %q, %s) end",
    name, mode, tostring(tonumber(value) or 1)))
end

local function setPause(on)
  if simTimeAuthority and simTimeAuthority.pause then
    simTimeAuthority.pause(on)
  else
    tapAction("pause")
  end
end

-- on/off features handled on the game engine side
local geToggles = {
  ["pause"] = {
    get = function() return simTimeAuthority and simTimeAuthority.getPause and simTimeAuthority.getPause() end,
    set = setPause,
  },
  ["slowmo"] = {
    get = function() return simTimeAuthority and simTimeAuthority.getReal and simTimeAuthority.getReal() < 0.999 end,
    -- the action toggles; only called when the state differs
    set = function(on)
      if not tapAction("toggle_slow_motion") and simTimeAuthority and simTimeAuthority.set then
        pcall(simTimeAuthority.set, on and 0.5 or 1)
      end
    end,
  },
}

-- on/off features handled in vehicle Lua (they report their own state there)
local vehicleToggles = {
  "ignition", "lights.low", "lights.high", "lights.fog", "lights.light_bar", "signal.left", "signal.right",
  "hazard", "parkingbrake", "esc", "tcs", "diff.front", "diff.center", "diff.rear", "awd", "range", "cruise",
}

-- one-shot and hold commands: { action = BeamNG action to run, hold = true if 1/0 press and release,
-- vehicle = true to run in vehicle Lua (as primary, or as fallback when the action is missing), fn = GE function }
local oneShots = {
  ["starter"] = { vehicle = true, hold = true },
  ["horn"] = { vehicle = true, hold = true },
  ["gearbox.mode"] = { vehicle = true, valued = true },
  ["gear.up"] = { action = "shiftUp", vehicle = true },
  ["gear.down"] = { action = "shiftDown", vehicle = true },
  ["cruise.set"] = { vehicle = true },
  ["cruise.up"] = { vehicle = true },
  ["cruise.down"] = { vehicle = true },
  ["camera.next"] = { fn = function() core_camera.setVehicleCameraByIndexOffset(0, 1) end, action = "switch_camera_next" },
  ["camera.prev"] = { fn = function() core_camera.setVehicleCameraByIndexOffset(0, -1) end, action = "switch_camera_prev" },
  ["recover"] = { action = "recover_vehicle", vehicle = true, hold = true },
  ["reset"] = { action = "reset_physics", vehicle = true },
  ["reset.full"] = { action = "reset_all_physics", fn = function() be:queueAllObjectLua("obj:requestReset(RESET_PHYSICS)") end },
  ["slowmo.faster"] = { action = "faster_motion" },
  ["slowmo.slower"] = { action = "slower_motion" },
}

-- names in the "commands" list sent to the companion
local function commandList()
  local list = {}
  for _, name in ipairs(vehicleToggles) do
    table.insert(list, name); table.insert(list, name .. ".toggle")
  end
  for name in pairs(geToggles) do
    table.insert(list, name); table.insert(list, name .. ".toggle")
  end
  for name in pairs(oneShots) do table.insert(list, name) end
  table.sort(list)
  return list
end

local vehicleToggleSet = {}
for _, name in ipairs(vehicleToggles) do vehicleToggleSet[name] = true end

local function runOneShot(name, def, value)
  local down = value >= 0.5
  if def.valued then
    vehicleCommand(name, "once", value)
    return
  end
  if def.hold then
    -- press on 1, release on 0
    if down == (heldActions[name] ~= nil) then return end
    if not down then
      local route = heldActions[name]
      heldActions[name] = nil
      if route == "action" then runAction(def.action, 0) else vehicleCommand(name, "once", 0) end
    elseif def.action and runAction(def.action, 1) then
      heldActions[name] = "action"
    elseif def.vehicle then
      heldActions[name] = "vehicle"
      vehicleCommand(name, "once", 1)
    end
    return
  end
  if not down then return end -- one-shots fire on the rising edge only
  if def.fn then
    local ok = pcall(def.fn)
    if ok then return end
  end
  if def.action and tapAction(def.action) then return end
  if def.vehicle then vehicleCommand(name, "once", 1) end
end

local function handleCommand(command, value)
  if type(command) ~= "string" then return end
  value = tonumber(value) or 1

  -- action.<beamngActionName>: press / release any BeamNG input action
  local actionName = command:match("^action%.([%w_]+)$")
  if actionName then
    local down = value >= 0.5
    if not down and not heldActions[command] then return end
    -- a second press without a release in between: release first so the action sees a fresh press
    if down and heldActions[command] then runAction(actionName, 0) end
    heldActions[command] = down or nil
    runAction(actionName, down and 1 or 0)
    return
  end

  local base, isToggle = command:match("^(.-)%.toggle$"), false
  if base then isToggle = true else base = command end

  if vehicleToggleSet[base] then
    if isToggle then
      if value >= 0.5 then vehicleCommand(base, "toggle", 1) end
    else
      vehicleCommand(base, "set", value)
    end
    return
  end

  local g = geToggles[base]
  if g then
    if isToggle and value < 0.5 then return end
    local ok, current = pcall(g.get)
    current = ok and current == true
    local want = isToggle and not current or (not isToggle and value >= 0.5)
    if want ~= current then g.set(want) end
    readGameState()
    return
  end

  local o = oneShots[command]
  if o then
    runOneShot(command, o, value)
    return
  end
  logW("unknown command " .. command)
end

-- ============= BINDINGS =============
-- Writes a default input map for the vJoy (or Bluetooth HID) device in the user folder, in the same
-- layout as the game's own settings/inputmaps/<vidpid>.json files, then reloads the input bindings.

local function bindingsPath(vidpid)
  return "/settings/inputmaps/" .. vidpid .. BINDINGS_SUFFIX .. ".json"
end

local function applyBindings(msg)
  local device = tostring(msg.device or "vjoy1")
  local vidpid = type(msg.vidpid) == "string" and string.lower(msg.vidpid) or nil
  if not vidpid and device:match("^vjoy%d*$") then vidpid = VJOY_VIDPID end
  if not vidpid or not vidpid:match("^%x%x%x%x%x%x%x%x$") then
    send({ type = "bindings.result", ok = false, device = device,
      message = "Could not find the input device for " .. device .. ". Make sure it is connected and try again." })
    return
  end

  local known = loadActions()
  local list, unknown, seen = {}, {}, {}
  for _, b in ipairs(type(msg.bindings) == "table" and msg.bindings or {}) do
    local action = type(b) == "table" and b.beamngAction
    local button = type(b) == "table" and tonumber(b.button)
    if type(action) == "string" and action:match("^[%w_]+$") and button and button >= 1 and button <= 128 and button % 1 == 0 then
      local control = "button" .. tostring(button - 1)
      local key = control .. "|" .. action
      if not seen[key] then
        seen[key] = true
        table.insert(list, { control = control, action = action })
        if next(known) and not known[action] then table.insert(unknown, action) end
      end
    end
  end

  local path = bindingsPath(vidpid)
  local ok, err = pcall(function()
    if #list == 0 then
      if FS:fileExists(path) then FS:removeFile(path) end
    else
      local deviceName = device:match("^vjoy") and "vJoy Device" or "GameCtl Pad"
      writeFile(path, encode({ name = deviceName, vendor = "", vidpid = vidpid, devicetype = "joystick", bindings = list }))
    end
    if core_input_bindings and core_input_bindings.onFileChanged then core_input_bindings.onFileChanged(path) end
  end)
  if not ok then
    send({ type = "bindings.result", ok = false, device = device, message = "Could not write the BeamNG bindings: " .. tostring(err) })
    return
  end

  local label = device:match("^vjoy") and ("vJoy device " .. (device:match("^vjoy(%d+)$") or "1")) or "the phone controller"
  if #list == 0 then
    local message = "Removed the in-game bindings for " .. label
    send({ type = "bindings.result", ok = true, device = device, count = 0, message = message })
    toast("info", message)
    return
  end
  -- give the input system a moment, then check it really loaded them before reporting ok
  pendingBindings = { wait = BINDINGS_VERIFY_DELAY, device = device, vidpid = vidpid, list = list,
    unknown = unknown, label = label, path = path }
end

-- true: the input system has every binding for this device; false: it has the device but not
-- our bindings; nil: it can't be checked (device not plugged in, or this game version keeps
-- bindings somewhere else)
local function bindingsLoaded(vidpid, list)
  local cib = rawget(_G, "core_input_bindings")
  if type(cib) ~= "table" or type(cib.bindings) ~= "table" then return nil end
  local found = nil
  for _, entry in pairs(cib.bindings) do
    if type(entry) == "table" then
      local contents = type(entry.contents) == "table" and entry.contents or entry
      local vp = contents.vidpid or entry.vidpid
      if type(vp) == "string" and string.lower(vp) == vidpid and type(contents.bindings) == "table" then
        local have = {}
        for _, b in pairs(contents.bindings) do
          if type(b) == "table" then have[tostring(b.control) .. "|" .. tostring(b.action)] = true end
        end
        local all = true
        for _, b in ipairs(list) do
          if not have[b.control .. "|" .. b.action] then all = false break end
        end
        if all then return true end
        found = false
      end
    end
  end
  return found
end

finishBindings = function()
  local p = pendingBindings
  pendingBindings = nil
  if not p then return end
  local okCheck, loaded = pcall(bindingsLoaded, p.vidpid, p.list)
  if not okCheck then loaded = nil end
  local n = #p.list
  local plural = n == 1 and "" or "s"
  local result = { type = "bindings.result", device = p.device, count = n, written = true,
    unknown = #p.unknown > 0 and p.unknown or nil }
  if loaded then
    result.ok = true
    result.message = string.format("Bound %d action%s to %s", n, plural, p.label)
  elseif loaded == false then
    result.ok = false
    result.message = string.format("Saved %d binding%s for %s, but BeamNG did not load them. Check Options > Controls in the game.", n, plural, p.label)
  else
    result.ok = false
    result.message = string.format("Saved %d binding%s for %s. BeamNG could not confirm they are active: make sure the controller is connected, then check Options > Controls.", n, plural, p.label)
  end
  if #p.unknown > 0 then result.message = result.message .. " Not found in this game version: " .. table.concat(p.unknown, ", ") .. "." end
  send(result)
  toast(result.ok and "info" or "warning", result.message)
end

-- ============= MESSAGES =============

local function handleMessage(line)
  local msg = decode(line)
  if not msg then
    send({ type = "error", code = "bad_message", message = "not JSON" })
    return
  end
  if type(msg.v) == "number" and msg.v > PROTOCOL_VERSION then
    send({ type = "error", code = "version_mismatch", message = "mod supports protocol " .. PROTOCOL_VERSION })
    flush()
    closeSocket()
    return
  end
  local t = msg.type
  if t == "command" then
    handleCommand(msg.command, msg.value)
  elseif t == "bindings.apply" then
    applyBindings(msg)
  elseif t == "state.request" then
    requestFullState()
  elseif t == "ping" then
    send({ type = "pong", t = msg.t })
  end
end

local function onConnected()
  status = "connected"
  -- Nagle plus delayed ACKs would add ~200 ms per message on Windows; set it on the connected socket
  local ok, err = pcall(sock.setoption, sock, "tcp-nodelay", true)
  if not ok then logW("could not set tcp-nodelay: " .. tostring(err)) end
  logI("connected to the companion")
  toast("success", "Connected to the Game Controller companion")
  send({ type = "hello", role = "beamng", modVersion = MOD_VERSION, gameVersion = tostring(beamng_versionb or beamng_version or "unknown") })
  send({ type = "commands", list = commandList() })
  requestFullState()
end

local function onDisconnected(reason)
  logI("companion connection closed: " .. tostring(reason))
  if status == "connected" then toast("warning", "Game Controller companion disconnected") end
  -- release anything held so nothing stays pressed
  for name, route in pairs(heldActions) do
    local def = oneShots[name]
    local actionName = name:match("^action%.([%w_]+)$")
    if route == "vehicle" then
      pcall(vehicleCommand, name, "once", 0)
    elseif actionName or (def and def.action) then
      pcall(runAction, actionName or def.action, 0)
    end
  end
  heldActions = {}
  closeSocket()
end

local function inProgress(err)
  return err == "timeout" or err == "Operation already in progress" or err == "Operation now in progress"
end

local function startConnect()
  sock = socket.tcp()
  sock:settimeout(0)
  local ok, err = sock:connect(HOST, PORT)
  if ok then
    onConnected()
  elseif inProgress(err) then
    status = "connecting"
    connectTimer = 0
  else
    closeSocket()
  end
end

-- Calling connect again reports the handshake result on every platform (a refused connect on
-- Windows only shows up in the except set, which select does not watch).
local function pollConnecting(dt)
  connectTimer = connectTimer + dt
  local ok, err = sock:connect(HOST, PORT)
  if ok or err == "already connected" then
    if sock:getpeername() then onConnected() else closeSocket() end
  elseif inProgress(err) then
    if connectTimer > CONNECT_TIMEOUT then closeSocket() end
  else
    closeSocket() -- refused or failed; retry after RETRY_INTERVAL
  end
end

local function receive()
  while sock and status == "connected" do
    local line, err, partial = sock:receive("*l", recvPartial)
    if line then
      recvPartial = ""
      if #line > 0 then handleMessage(line) end
    elseif err == "timeout" then
      recvPartial = partial or ""
      return
    else
      onDisconnected(err)
      return
    end
  end
end

-- ============= HOOKS =============

local function onUpdate(dtReal)
  dtReal = tonumber(dtReal) or 0
  if not socket then return end
  if status == "idle" then
    retryTimer = retryTimer + dtReal
    if retryTimer >= RETRY_INTERVAL then
      retryTimer = 0
      startConnect()
    end
    return
  end
  if status == "connecting" then
    pollConnecting(dtReal)
    return
  end

  receive()
  if status ~= "connected" then return end

  gameTimer = gameTimer + dtReal
  if gameTimer >= 0.25 then
    gameTimer = 0
    readGameState()
  end

  if pendingBindings then
    pendingBindings.wait = pendingBindings.wait - dtReal
    if pendingBindings.wait <= 0 then finishBindings() end
  end

  -- reload the vehicle extension if the player vehicle went quiet (reloaded with Ctrl+R, new vehicle...).
  -- Vehicle Lua may not run while the game is paused, so don't count that time.
  if not state["game.paused"] then vehicleSilence = vehicleSilence + dtReal end
  local playerId = be:getPlayerVehicleID(0)
  if playerId == nil or playerId < 0 then
    if vehicleId ~= nil or fullPending then attachVehicle() end -- left the vehicle: report game state only
  elseif (playerId ~= vehicleId and vehicleSilence > 0.5) or vehicleSilence > VEHICLE_TIMEOUT then
    attachVehicle()
  end

  flushState()
  flush()
end

local function onVehicleSwitched(oldId, newId)
  if oldId and oldId >= 0 then
    local old = be:getObjectByID(oldId)
    if old then old:queueLuaCommand("local m = extensions.gameControllerVehicle if m then m.setActive(false) end") end
  end
  vehicleId = nil
  actions = nil
  if status == "connected" then attachVehicle() end
end

local function onVehicleSpawned(id)
  if id == be:getPlayerVehicleID(0) then
    vehicleId = nil
    actions = nil
    if status == "connected" then attachVehicle() end
  end
end

local function onExtensionUnloaded()
  if status == "connected" then onDisconnected("mod unloaded") end
  closeSocket()
end

M.onUpdate = onUpdate
M.onVehicleSwitched = onVehicleSwitched
M.onVehicleSpawned = onVehicleSpawned
M.onExtensionUnloaded = onExtensionUnloaded

M.onVehicleState = onVehicleState
M.runAction = runAction
M.handleCommand = handleCommand

-- exposed for the offline tests
M._handleMessage = handleMessage
M._commandList = commandList
M._substitute = substitute
M._status = function() return status end

return M
