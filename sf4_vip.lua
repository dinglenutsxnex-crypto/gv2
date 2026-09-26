-- Shadow Fight 4 VIP (libsf4.so / Action-Mods) -> simplified CE Lua
-- Target: SF4 on emulator (LDPlayer/Bluestacks) attached via Cheat Engine
-- Origin: sub_E4E0 cases + workers sub_10264,10514,10728,109B0,10B48,10DB8,10F48,110D8,113D0,115D0
-- Helpers in binary: scan(map,needle) = sub_1DDC0, read= sub_1CF40, write= sub_1D108/1D090/1D180
-- getpid() calls in binary are junk, ignored here.
-- Usage in CE: load table, execute, call SF4_Toggle("immortal", true) etc. from Lua engine.

if getCEVersion == nil then
  print("[SF4] Not running inside Cheat Engine, functions still loadable in GG with edits")
end

SF4 = { saved = {} }

local function aobScanFirst(pattern)
  -- pattern like "00 00 E0 00" or string "14680064"
  local res = AOBScan(pattern, "+W-C")
  if res == nil then return nil end
  local addr = res[0]
  res.destroy()
  return addr
end

local function scanAll(pattern)
  if type(pattern) == "number" then pattern = tostring(pattern) end
  local out = {}
  local res = AOBScan(pattern, "+W-C")
  if res == nil then return out end
  for i=0,res.Count-1 do out[#out+1] = res[i] end
  res.destroy()
  return out
end

function SF4_PatchDword(oldVal, newVal)
  local addrs = scanAll(oldVal)
  for _,a in ipairs(addrs) do
    if SF4.saved[a] == nil then SF4.saved[a] = readInteger(a) end
    writeInteger(a, newVal)
  end
  return #addrs
end

-- 2_Toggle_Long Range Hits (case 2)
-- ON: scan 14680064 -> write 1468006400 / OFF: reverse
-- sub_10264 / sub_10514, filter neighbours 0,1,0,0,1 in binary, simplified here
function SF4_LongRange(on)
  if on then return SF4_PatchDword(14680064, 1468006400)
  else return SF4_PatchDword(1468006400, 14680064) end
end

-- 14_Toggle_One Hit Kill (case 14, sub_10DB8)
function SF4_OneHitKill(on)
  if on then return SF4_PatchDword(1258291, 125829100)
  else return SF4_PatchDword(125829100, 1258291) end
end

-- 12_Button_Summon Ai Opponent (case 12, sub_109B0, one-shot)
function SF4_SummonAI()
  return SF4_PatchDword(46000, 0)
end

-- 13_Toggle_Speed Hack (case 13, sub_10B48 + master 11FCC)
-- binary: scan 1.0f (1065353216), filter 1051372203/1022739087, collect CB160, master writes 10.0f
function SF4_Speed(on)
  local addrs = scanAll("3F 80 00 00", "+W-C") -- 1.0f
  local n = 0
  for _,a in ipairs(addrs) do
    if SF4.saved[a] == nil then SF4.saved[a] = readFloat(a) end
    writeFloat(a, on and 10.0 or 1.0)
    n = n + 1
    if n > 200 then break end -- safety, narrow manually to SF4 lib range
  end
  return n
end

-- 9_Toggle_Max Time (case 9, sub_10728)
-- pattern "1891511843495608320" (CB828), timer list CB148, master writes 1903165440
function SF4_MaxTime(on)
  -- simplified: patch timer values found via pattern, master in binary writes 1903165440
  local addrs = scanAll("1891511843495608320", "+W-C")
  return #addrs -- inspect manually, then patch +20 off like binary: writeInteger(addr+20, 1903165440)
end

-- Entity-based (need CB118 player list built by sub_11820). Offsets from sub_11FCC:
-- immortal:        [entity+153144] = 0x100000 (CB184, case 6)
-- max shadow:      [entity+153152] = 0x100000 (CB18C, case 8)
-- freeze enemy:    [entity-424] = 0            (CB188, case 7, applied on disable)
-- enemy no ability:[entity+162744] = 0         (CB1A4, case 15)
-- autowin:         [entity+162736] = 0         (case 5, sub_1B7F8)
-- max time live:   [timer+0] = 1903165440      (CB190)
-- speed live:      [speedAddr] = 10.0 float    (CB19C)
-- To use in CE: find player base via pointer scan, then:
--   writeInteger(base+153144, 0x100000) -- immortal
--   writeInteger(base+153152, 0x100000) -- shadow
--   writeInteger(base-424, 0)           -- freeze
--   writeInteger(base+162744, 0)        -- enemy no-ability
--   writeInteger(base+162736, 0)        -- autowin

-- 1_Spinner_Instant Win (case 1): dword_CB178 = sel (0=None,1=1v1,2=3v3,3=Story,4=Chronicles)
function SF4_InstantWin(sel) writeInteger("SF4_CB178", sel) end -- replace with real addr

-- 20/22 level spinners (case 20/22, sub_10F48(id) -> write_bool(id,1))
SF4_LVL_1v1 = {"64424509440001","85899345920001","128849018880001","193273528320001"}
SF4_LVL_3v3 = {"128849018880001","193273528320001","257698037760001","515396075520001"}
function SF4_SelectLevel(idStr)
  local addrs = scanAll(idStr)
  for _,a in ipairs(addrs) do writeInteger(a, 1, 1) end -- sub_1D090(map,1,addr)
  return #addrs
end

-- 24_Spinner_Event Only AI Enemy (case 24, sub_113D0(a,b) -> write 0,0)
SF4_EVENT = {
  {"40000","50000"}, {"55000","65000"}, {"85000","95000"},
  {"100000","110000"}, {"120000","130000"}, {"180000","190000"},
}
function SF4_Event(idx)
  local p = SF4_EVENT[idx]
  if not p then return 0 end
  local n = SF4_PatchDword(tonumber(p[1]), 0)
  n = n + SF4_PatchDword(tonumber(p[2]), 0)
  return n
end

-- 21/23_InputValue_Level (case 21/23, sub_110D8(v): sprintf+scan, write 0)
function SF4_CustomRank(level)
  return SF4_PatchDword(level, 0) -- simplified, binary checks 9 guards != -1
end

-- 25_InputValue_Max Upgrade (case 25, sub_115D0(v): scan, if guard then write 13 at -36)
function SF4_MaxUpgrade(level)
  local addrs = scanAll(tostring(level), "+W-C")
  for _,a in ipairs(addrs) do writeInteger(a-36, 13) end
  return #addrs
end

-- generic dispatcher mirroring sub_E4E0(a4,a6,a7)
function SF4_Toggle(name, on, sel)
  name = name:lower()
  if name=="longrange" then return SF4_LongRange(on)
  elseif name=="onehit" then return SF4_OneHitKill(on)
  elseif name=="summon" then return SF4_SummonAI()
  elseif name=="speed" then return SF4_Speed(on)
  elseif name=="maxtime" then return SF4_MaxTime(on)
  elseif name=="autowin_1v1" then return SF4_SelectLevel(SF4_LVL_1v1[sel or 1])
  elseif name=="autowin_3v3" then return SF4_SelectLevel(SF4_LVL_3v3[sel or 1])
  elseif name=="event" then return SF4_Event(sel or 1)
  elseif name=="maxupgrade" then return SF4_MaxUpgrade(sel or 5)
  else print("unknown "..name) return 0 end
end

print("[SF4 VIP] lua loaded. e.g. SF4_Toggle('onehit',true)")
