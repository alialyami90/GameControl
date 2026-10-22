-- Minimal JSON for the offline tests (stands in for BeamNG's jsonEncode / jsonDecode).
local M = {}

local function isArray(t)
  local n = 0
  for _ in pairs(t) do n = n + 1 end
  if n == 0 then return false end
  for i = 1, n do if t[i] == nil then return false end end
  return true
end

local escapes = { ['"'] = '\\"', ['\\'] = '\\\\', ['\n'] = '\\n', ['\r'] = '\\r', ['\t'] = '\\t' }

local function encode(v)
  local t = type(v)
  if t == "nil" then return "null"
  elseif t == "boolean" then return tostring(v)
  elseif t == "number" then
    if v ~= v or v == math.huge or v == -math.huge then return "null" end
    if v % 1 == 0 and math.abs(v) < 1e15 then return string.format("%d", v) end
    return string.format("%.14g", v)
  elseif t == "string" then
    return '"' .. v:gsub('[%c"\\]', function(c) return escapes[c] or string.format("\\u%04x", c:byte()) end) .. '"'
  elseif t == "table" then
    local out = {}
    if isArray(v) then
      for _, x in ipairs(v) do table.insert(out, encode(x)) end
      return "[" .. table.concat(out, ",") .. "]"
    end
    for k, x in pairs(v) do table.insert(out, encode(tostring(k)) .. ":" .. encode(x)) end
    return "{" .. table.concat(out, ",") .. "}"
  end
  error("cannot encode " .. t)
end

local function decode(s)
  local pos = 1
  local function ws() pos = s:find("[^%s]", pos) or #s + 1 end
  local value
  local function str()
    local out = {}
    pos = pos + 1
    while true do
      local c = s:sub(pos, pos)
      if c == '"' then pos = pos + 1; break
      elseif c == "\\" then
        local n = s:sub(pos + 1, pos + 1)
        local map = { n = "\n", r = "\r", t = "\t", b = "\b", f = "\f" }
        if n == "u" then table.insert(out, string.char(tonumber(s:sub(pos + 2, pos + 5), 16) % 256)); pos = pos + 6
        else table.insert(out, map[n] or n); pos = pos + 2 end
      elseif c == "" then error("unterminated string")
      else table.insert(out, c); pos = pos + 1 end
    end
    return table.concat(out)
  end
  function value()
    ws()
    local c = s:sub(pos, pos)
    if c == "{" then
      local t = {}
      pos = pos + 1; ws()
      if s:sub(pos, pos) == "}" then pos = pos + 1 return t end
      while true do
        ws(); local k = str(); ws()
        assert(s:sub(pos, pos) == ":", "expected :"); pos = pos + 1
        t[k] = value(); ws()
        local d = s:sub(pos, pos); pos = pos + 1
        if d == "}" then return t end
        assert(d == ",", "expected ,")
      end
    elseif c == "[" then
      local t = {}
      pos = pos + 1; ws()
      if s:sub(pos, pos) == "]" then pos = pos + 1 return t end
      while true do
        table.insert(t, value()); ws()
        local d = s:sub(pos, pos); pos = pos + 1
        if d == "]" then return t end
        assert(d == ",", "expected ,")
      end
    elseif c == '"' then return str()
    elseif s:sub(pos, pos + 3) == "true" then pos = pos + 4 return true
    elseif s:sub(pos, pos + 4) == "false" then pos = pos + 5 return false
    elseif s:sub(pos, pos + 3) == "null" then pos = pos + 4 return nil
    else
      local num = s:match("^-?%d+%.?%d*[eE]?[-+]?%d*", pos)
      assert(num and #num > 0, "bad json at " .. pos)
      pos = pos + #num
      return tonumber(num)
    end
  end
  return value()
end

M.encode = encode
M.decode = decode
return M
