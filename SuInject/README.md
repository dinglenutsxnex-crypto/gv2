# SuInject – root injector like AMODSVM but offline

Same VM + root only. No server, no key. Uses your `vip/libsf4.so` (ghost has no internal auth).

## Quick test, no app build

1. Copy to phone (same root VM as SF4): `SuInject/` + `libsf4.so` (from `vip/`) to `/sdcard/SuInject/`
2. Build `injector` once (PC with NDK):
   `aarch64-linux-android21-clang injector.c -o injector`
   push it next to `inject.sh`. Or use frida-server instead (below).
3. Start SF4, then Termux: `su -c "sh /sdcard/SuInject/inject.sh"`
4. Wait 3s (`sleep` in JNI_OnLoad), grant overlay to SF4 if asked. Menu = 26 features.

Frida fallback (no ptrace coding): `su -c ./frida-server &`, then `frida -U -f com.nekki.shadowfight4 -l run.js` with `Module.load("/data/local/tmp/libsf4.so")`.

## App (AMODSVM-style big button)

* New Android Studio project, pkg `com.sf4injector`, minSdk 26 (needs InMemoryDexClassLoader in ghost).
* Add `app/src/main/assets/libsf4.so` (yours) + `assets/injector` (built above).
* Replace `MainActivity` with `MainActivity.kt` here. Manifest needs:
  `REQUEST_INSTALL_PACKAGES` no, just `SYSTEM_ALERT_WINDOW` note for SF4 + `REQUEST_SU` (root apps declare nothing special, just exec `su`).
* Build, install in same VM, tap INJECT.

## Notes

* `injector.c` here is intentionally a stub that resolves remote `dlopen/mmap` addrs and attaches — finish the remote-call part from any public `android ptrace dlopen` sample, or skip it and use frida-server (recommended, SELinux-friendly).
* SF4 must be arm64, running, same user namespace (same VM). `pidof com.nekki.shadowfight4` must return a pid.
* If menu doesn't draw: `appops set com.nekki.shadowfight4 SYSTEM_ALERT_WINDOW allow` as root.
