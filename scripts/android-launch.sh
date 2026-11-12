#!/bin/bash
# Cold-starts the app and prints its UNIFIEDPREFS_STATE line (nothing for the before-state app).
source "$(dirname "$0")/_common.sh"; require_android
"$ADB" shell am force-stop "$APP_ID"
"$ADB" logcat -c
"$ADB" shell am start -W -n "$APP_ID/.MainActivity" >/dev/null
sleep 2
"$ADB" logcat -d -s UnifiedPrefs:I | grep -o "UNIFIEDPREFS_STATE.*" | tail -1 || true
