#!/system/bin/sh
# SF4 root inject test - no app build needed. Run in Termux/MTerm as root, same VM as SF4.
# Needs: libsf4.so next to this script, SF4 running (com.nekki.shadowfight4, arm64)
# Usage: su -c "sh /sdcard/SuInject/inject.sh"

LIBSRC="$(dirname "$0")/libsf4.so"
[ -f "$LIBSRC" ] || LIBSRC="/sdcard/SuInject/libsf4.so"
DST="/data/local/tmp/libsf4.so"
PKG="com.nekki.shadowfight4"

cp "$LIBSRC" "$DST" || exit 1
chmod 755 "$DST"
# SELinux: keep in tmp, chcon to app_data so target can dlopen
chcon u:object_r:shell_data_file:s0 "$DST" 2>/dev/null

PID=$(pidof "$PKG" | awk '{print $1}')
if [ -z "$PID" ]; then echo "start SF4 first"; exit 1; fi
echo "SF4 pid=$PID"

# injector binary next to script (build injector.c with NDK, or use bundled one)
INJ="$(dirname "$0")/injector"
[ -x "$INJ" ] || INJ="/sdcard/SuInject/injector"
if [ ! -x "$INJ" ]; then echo "missing injector binary, build injector.c"; exit 1; fi

# ptrace dlopen: ./injector <pid> <libpath>
"$INJ" "$PID" "$DST"
echo "exit=$? (JNI_OnLoad sleeps 3s, menu appears as overlay - grant SYSTEM_ALERT_WINDOW to SF4)"
