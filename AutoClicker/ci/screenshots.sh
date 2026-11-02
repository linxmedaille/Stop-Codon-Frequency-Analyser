#!/usr/bin/env bash
# Drives the debug build on an emulator and captures screenshots of every screen.
# Used by CI; also handy locally with a running emulator: `bash ci/screenshots.sh`.
set -uo pipefail

PKG=com.tapflow.autoclicker
APK=${APK:-out/tapflow-debug.apk}
OUT=screenshots
mkdir -p "$OUT"
rm -f "$OUT"/*.png

alive() { [ "$(timeout 10 adb get-state 2>/dev/null)" = "device" ]; }
shot() { # name [delay]
  sleep "${2:-4}"
  if ! alive; then echo "emulator is gone; stopping early"; exit 0; fi
  timeout 30 adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}
open_page() { adb shell am start -n "$PKG/.MainActivity" --ei page "$1" > /dev/null; }

# Tap the centre of the first UI node whose text or content-desc matches.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import re, subprocess, sys
want = sys.argv[1]
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x = (int(b.group(1)) + int(b.group(3))) // 2
        y = (int(b.group(2)) + int(b.group(4))) // 2
        subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])
        print(f"tapped '{want}' at {x},{y}")
        sys.exit(0)
print(f"'{want}' not found")
sys.exit(1)
PY
}

adb wait-for-device
# Keep going if a dialog would block the screen; ANRs/crashes are reported from logcat at the end.
adb shell settings put global hide_error_dialogs 1
adb logcat -c
adb shell getprop ro.build.version.release
adb shell wm size
adb install -r "$APK"

# 1. A "target app" screenshot to put on the boards: the system Settings screen.
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 4
adb exec-out screencap -p > "$OUT/_target_app.png"
adb shell input keyevent HOME

# 2. Seed a demo workspace that uses it.
SIZE=$(python3 -c "import struct;d=open('$OUT/_target_app.png','rb').read(24);print(*struct.unpack('>II',d[16:24]))")
read -r W H <<< "$SIZE"
echo "target screenshot ${W}x${H}"
SHOT="/data/user/0/$PKG/files/screens/demo.png"
cat > /tmp/ws.json <<JSON
{"version":1,
 "single":{"board":{"screenshot":{"path":"$SHOT","width":$W,"height":$H},"targets":[{"id":"s1","name":"P1","point":{"x":0.5,"y":0.42}}]},"intervalMs":100,"holdMs":20},
 "multi":{"board":{"screenshot":{"path":"$SHOT","width":$W,"height":$H},"targets":[
   {"id":"m1","name":"P1","point":{"x":0.3,"y":0.30}},
   {"id":"m2","name":"P2","point":{"x":0.62,"y":0.46}},
   {"id":"m3","name":"P3","point":{"x":0.42,"y":0.63}}]},
   "delays":{"m2":2000},"defaultDelayMs":500},
 "chain":{"board":{"screenshot":{"path":"$SHOT","width":$W,"height":$H},"targets":[
   {"id":"p1","name":"P1","point":{"x":0.3,"y":0.42}},
   {"id":"p2","name":"P2","point":{"x":0.7,"y":0.62}}]}},
 "lastMode":"Single","onboarded":true}
JSON
adb shell am force-stop "$PKG"
adb shell "run-as $PKG mkdir -p files/screens"
# (adb push + run-as cp: piping binaries through `adb shell` stdin corrupts them)
adb push "$OUT/_target_app.png" /data/local/tmp/demo.png > /dev/null
adb push /tmp/ws.json /data/local/tmp/ws.json > /dev/null
adb shell chmod 644 /data/local/tmp/demo.png /data/local/tmp/ws.json
adb shell "run-as $PKG cp /data/local/tmp/demo.png files/screens/demo.png"
adb shell "run-as $PKG cp /data/local/tmp/ws.json files/workspace.json"
adb shell "run-as $PKG ls -la files files/screens"

# 3. Pages, light theme.
adb shell cmd uimode night no
open_page 0; shot 01_single 12
open_page 1; shot 02_multi
open_page 2; shot 03_chain
open_page 3; shot 04_settings

# 4. Mid-swipe: the liquid page transition and stretched dock lens.
open_page 0; sleep 2
adb shell input swipe 950 1500 150 1500 1800 &
sleep 0.7; adb exec-out screencap -p > "$OUT/05_swipe_mid.png"; echo "captured 05_swipe_mid"
wait; sleep 1

# 5. Scrolled content.
open_page 2; sleep 2
adb shell input swipe 540 1700 540 600 500; shot 06_chain_scrolled
open_page 0; sleep 2
adb shell input swipe 540 1700 540 500 500; shot 07_single_scrolled

# 6. Sheets (opened via debug intents: uiautomator can't dump an always-animating UI).
open_page 0; sleep 2
adb shell am start -n "$PKG/.MainActivity" --es sheet permission > /dev/null; shot 08_permission_sheet
adb shell input keyevent BACK; sleep 1
open_page 2; sleep 2
adb shell am start -n "$PKG/.MainActivity" --es sheet count > /dev/null; shot 09_count_sheet
adb shell input keyevent BACK; sleep 1
adb shell am start -n "$PKG/.MainActivity" --es sheet duration > /dev/null; shot 09b_duration_sheet
adb shell input keyevent BACK; sleep 1

# 7. Board editor (full screen).
adb shell am start -n "$PKG/.MainActivity" --es editor Multi > /dev/null; shot 10_editor
adb shell input keyevent BACK; sleep 1

# 8. Dark theme.
adb shell cmd uimode night yes
open_page 2; shot 11_chain_dark 4
open_page 0; shot 12_single_dark
open_page 1; shot 13_multi_dark
adb shell cmd uimode night no

# 9. Turn the service on and run: floating panel + markers over another app.
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.service.ClickerService"
adb shell settings put secure accessibility_enabled 1
sleep 3
open_page 0; sleep 3; shot 14_single_service_on 1
adb shell am start -n "$PKG/.MainActivity" --es start Single > /dev/null; sleep 3
adb shell am start -a android.settings.SETTINGS > /dev/null; shot 15_panel_armed 3
# The panel is its own overlay window; fall back to its known position (pixel_6, 420 dpi).
adb shell input tap 262 507
shot 16_running 6
adb shell input tap 407 507
shot 17_stopped 2

echo "---- ANRs / crashes (logcat) ----"
timeout 30 adb logcat -d | grep -E "ANR in|FATAL EXCEPTION|Choreographer.*Skipped [0-9]{3,}" | head -40 || true
timeout 30 adb logcat -d | grep -A 25 "FATAL EXCEPTION" | head -80 || true
echo "done"
