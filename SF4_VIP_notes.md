# SF4 VIP – libsf4.so notes

Game: **Shadow Fight 4**, menu: **Action-Mods v1.0** (`https://action-mods.com`).
Binary: `libsf4.so` (SONAME `libsf4vip.so`), ARM64, stripped, 1510 funcs, 1 export `JNI_OnLoad 0x135b0`. NDK libc++.

## Loader (JNI_OnLoad)

1. `prctl`, save 6 native ptrs to `CB040..CB068`, `pthread_create sub_15658`, `sleep 3`, `AttachCurrentThread`.
2. `FindClass android/app/ActivityThread` / `GetStaticMethodID currentApplication ()Lapp/Application;` -> app object.
3. Hex blob at `0x701c9` (`0x1D430` chars) hex-decodes to 59928b `dex035` (`dump.dex`).
4. `NewByteArray(59928)` / `GetByteArrayElements` / fill / `Release`.
5. `FindClass java/nio/ByteBuffer` / `GetStaticMethodID wrap ([B)LByteBuffer;`.
6. `FindClass java/lang/ClassLoader` / `GetStaticMethodID getSystemClassLoader`.
7. `FindClass dalvik/system/InMemoryDexClassLoader` / `GetMethodID <init> ([LByteBuffer;LClassLoader;)V` / `NewObject`.
8. `GetMethodID loadClass (LString;)LClass;` -> `loadClass("com.android.support.Menu")` -> `NewGlobalRef`.
9. `RegisterNatives(Menu, 6)` / `GetMethodID CreateMenu (LContext;)V` / call it. Returns `0x10006`.

All 29 JNI strings XOR+`veorq` with keys `6FDC0..6FEE0/6FF50..700C0` – see `decode.py`.

## DEX

`com.android.support.Menu` + ~30 `$1000000xx` widget classes. Natives:

| Java | sig | native |
|---|---|---|
| Icon | ()LString; | sub_12234 |
| IconWebViewData | ()LString; | sub_124B4 (`return 0`) |
| Init | (Context,TextView,TextView)V | sub_124BC |
| SettingsList | ()[String; | sub_12E64 |
| GetFeatureList | ()[String; | sub_C9B0 |
| Changes (static) | (Context,int,String,int,boolean,String)V | sub_E4E0 |
| CreateMenu | (Context)V | Java, called from native |

Init strings:

- `<b>Action Mods</b>`
- `<marquee><font color="#00FF00">Shadow Fight 4 VIP Mod Menu | https://action-mods.com </font> | <font color="#FFFFFF">v1.0</font></marquee>`
- `Modded by ActionMods`

Icon `sub_12234`: `memcpy(CBAD8,unk_8D7C4,0xBF95)` + NEON keystream (`6FCA0-6FD10`) -> `NewStringUTF` base64 PNG `iVBOR...Jggg==` (~49kb, `icon_dec.bin`).

SettingsList (5): `Category_Settings, -1_Toggle_Save feature preferences, -3_Toggle_Auto size vertically, Category_Menu, -6_Button_Close settings`.

GetFeatureList (26) – `sub_C9B0`, `NewObjectArray(26)`:

```
Category_Player Hack
5_Toggle_Autowin
1_Spinner_Instant Win_None,1v1,3v3,Story Mode,Chronicles Mode
6_Toggle_Immortal
8_Toggle_Max Shadow Ability
14_Toggle_One Hit Kill
2_Toggle_Long Range Hits
Category_Enemy Hack
7_Toggle_Freeze Enemy
15_Toggle_Enemy No Ability
Category_Character Hacks
11_Toggle_Ling Hack
25_InputValue_Max Upgrade Character
Category_Battle Hacks
9_Toggle_Max Time
13_Toggle_Speed Hack
Category_Rank Mode
10_Toggle_Real Player Hack (PvP)
12_Button_Summon Ai Opponent
Collapse_Real Player to AI
20_CollapseAdd_Spinner_1v1 Match_Lvl 1,Lvl 2,Lvl 3,Lvl 4
21_CollapseAdd_InputValue_Level 5 (Custom Rank)
22_CollapseAdd_Spinner_3v3 Match_Lvl 1,Lvl 2,Lvl 3,Lvl 4
23_CollapseAdd_InputValue_Level 5 (Custom Rank)
24_Spinner_Event Only AI Enemy_1st,2nd,3rd,4th,5th,6th,Last
RichTextView_ NOTE : <font...>If this mods aren't working then just click the fight button...</font>
```

## Changes – sub_E4E0(a4=feature, a6=sel/val, a7=on)

| ID | UI | case | impl |
|---|---|---|---|
| 1 | Instant Win spinner | `dword_CB178=a6` | stored only |
| 2 | Long Range | on:`FD94+sub_10264` if in-fight, off:`FD94+sub_10514` | `10264: scan "14680064", neighbours 0,1,0,0,1 -> write 1468006400`; `10514` reverse |
| 5 | Autowin | `CB180=on`, spawn `sub_1B7F8` | `for(p:CB118) write(p+162736,0)` |
| 6 | Immortal | `CB184=on` | master: `write(p+153144,0x100000)` |
| 7 | Freeze Enemy | `CB188=on`, off+in-fight restores `CB118-424=0x100000` loop | master: `write(p-424,0)` pattern |
| 8 | Max Shadow | `CB18C=on` | master: `write(p+153152,0x100000)` |
| 9 | Max Time | spawn `sub_10728` loop | scan `1891511843495608320`, filter `*(e-116)==361758720 && *(e-128)==0x28F..`, collect `e+20->CB148`; master writes `1903165440` |
| 10 | Real Player PvP | `CB194=on` | gate for masters |
| 11 | Ling Hack | `CB198=on` | gate |
| 12 | Summon AI (button) | spawn `sub_109B0` | `scan "46000" -> write 0` |
| 13 | Speed Hack | on:`sub_10B48`, off: restore `CB160=1.0f` | `scan "1065353216"(1.0f)`, filter `1051372203/1022739087` -> `CB160`; master writes `10.0f` |
| 14 | One Hit Kill | spawn `sub_10DB8` | `scan "1258291" -> write 125829100` |
| 15 | Enemy No Ability | `CB1A4=on` | master: `write(p+162744,0)` |
| 20 | 1v1 Lvl spinner | `sub_10F48(id)` | `64424509440001,85899345920001,128849018880001,193273528320001` -> `write_bool(id,1)` |
| 21/23 | Level InputValue | `sub_110D8(v)` | `sprintf+scan`, guards `!=-1`, `write(vaddr,0)` |
| 22 | 3v3 Lvl spinner | `sub_10F48` | `128849018880001,193273528320001,257698037760001,515396075520001` |
| 24 | Event Enemy spinner | `sub_113D0(a,b)` | `("40000","50000"),("55000","65000"),("85000","95000"),("100000","110000"),("120000","130000"),("180000","190000")` -> `write 0,0` |
| 25 | Max Upgrade InputValue | `sub_115D0(v)` | `scan v`, if guard then `write(v-36,13)` |
| any toggle | — | `LABEL_113` | if any `CB180/184/18C/188/190/19C/194/198/1A0/1A4` start `sub_11820+sub_11FCC`, else stop `D833C` |

Workers: `10F48(id)=scan+write1`, `110D8(v)=scan+write0`, `113D0(a,b)=scan+write0 x2`, `115D0(v)=scan+write13`.

Masters:

- `sub_11820` while `D833C`: scan `CBA48` pattern, build `CB118` entity vector (1s loop).
- `sub_11FCC` while `D833C` every 33ms: `CB19C->10.0f to CB160`; if in-fight `CB17C==1`: `CB18C/CB184/CB188/CB1A4` patches above + `CB190->1903165440 to CB148`.

## Anti-tamper – sub_15658

```cpp
while(!A && !B && !C) sleep(2); _exit(0);
// A=sub_19360 openat/maps check, B=sub_18FA8 dlopen("libc.so")+dlsym, C=sub_16E20 huge maps scan
// strings: /proc/%d/maps, %lx-%lx, kgsl-3d0, [anon:libc_malloc], [anon:.bss], /data/app/, [heap], dalvik, /system, [stack], /dev/ashmem/
```

## Files

- `%TEMP%/dump.dex`, `icon_dec.bin`, `icon_enc.bin`, `hexdex.txt`
- `%TEMP%/jni.c`, `func_*.c`, `work_*.c`, `sub_*.c`, `decode*.py`, `keys*.bin`
- `./sf4_vip.lua` – CE simplification (this folder)
