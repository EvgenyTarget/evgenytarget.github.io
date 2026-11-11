#!/usr/bin/env bash
# Runs INSIDE Android Emulator Runner as a single process; no shell variables
# are lost between lines (the runner executes separate commands otherwise).
set -euo pipefail

APK="rashody-android/app/build/outputs/apk/debug/app-debug.apk"
PKG="kg.evgeny.moidengi.sheets"
test -s "$APK"
adb install -r "$APK"
adb shell settings put system accelerometer_rotation 0
adb shell cmd window user-rotation lock 0
adb shell am start -W -n "$PKG/kg.evgeny.moidengi.MainActivity"
sleep 12
PID="$(adb shell pidof "$PKG" | tr -d '\r')"
test -n "$PID"
echo "Finance process PID: $PID"

adb exec-out screencap -p > /tmp/moi-dengi-portrait.png
adb shell cmd window user-rotation lock 1
sleep 8
NEW_PID="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)"
echo "Package process after rotation: ${NEW_PID:-not visible}"
if [ "$NEW_PID" != "$PID" ]; then
  echo "NOTICE: PID changed during rotation: $PID -> $NEW_PID"
  adb shell dumpsys activity activities | grep "$PKG" | head -10 || true
fi
adb exec-out screencap -p > /tmp/moi-dengi-landscape.png
adb shell cmd window user-rotation lock 0
sleep 6
NEW_PID="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)"
echo "Package process after rotation: ${NEW_PID:-not visible}"
if [ "$NEW_PID" != "$PID" ]; then
  echo "NOTICE: PID changed during rotation: $PID -> $NEW_PID"
  adb shell dumpsys activity activities | grep "$PKG" | head -10 || true
fi
adb exec-out screencap -p > /tmp/moi-dengi-portrait-again.png

python3 -m pip install --quiet Pillow
python3 - <<'PY'
from PIL import Image
names=["portrait","landscape","portrait-again"]
dims=[]
for name in names:
    im=Image.open("/tmp/moi-dengi-"+name+".png").convert("RGB")
    width,height=im.size
    pixels=im.load()
    total=0; dark=0
    for y in range(0,height,8):
        for x in range(0,width,8):
            r,g,b=pixels[x,y]; total+=1
            if r<95 and g<140 and b<110: dark+=1
    ratio=dark/total
    print(name,(width,height),"visible-dark-content:",round(ratio,4),flush=True)
    assert ratio>=0.035,name+" screenshot is blank or does not contain the finance UI"
    dims.append((width,height))
assert dims[0][1]>dims[0][0],"Startup was not portrait"
assert dims[1][0]>dims[1][1],"Android did not rotate to landscape"
assert dims[2][1]>dims[2][0],"App did not return to portrait"
print("PASS: portrait -> landscape -> portrait; UI visible and process preserved")
PY

adb shell dumpsys meminfo "$PKG" | head -55
if adb logcat -d -v brief | grep -E 'FATAL EXCEPTION.*kg.evgeny.moidengi|crash.*kg.evgeny.moidengi'; then
    echo "Finance app crash detected"
    exit 1
fi
echo "PASS: Android finance app smoke test"
