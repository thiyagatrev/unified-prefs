#!/bin/bash
# Cold-starts the app with any given arguments and prints its UNIFIEDPREFS_STATE line.
source "$(dirname "$0")/_common.sh"
boot_ios
xcrun simctl terminate "$IOS_SIM" "$APP_ID" >/dev/null 2>&1 || true
OUT=$(mktemp)
# --console-pty is the only mode that reliably delivers the app's stdout to the host.
xcrun simctl launch --console-pty "$IOS_SIM" "$APP_ID" "$@" > "$OUT" 2>&1 &
PID=$!
for _ in $(seq 1 30); do grep -q UNIFIEDPREFS_STATE "$OUT" && break; sleep 0.5; done
# Keep watching briefly: a crash after the state line (for example when the first screen
# appears) would otherwise go unnoticed.
sleep 3
if grep -qE "Uncaught Kotlin exception|Fatal error" "$OUT"; then
    grep -o "UNIFIEDPREFS_STATE.*" "$OUT" | tail -1 | tr -d '\r'
    echo "APP CRASHED AFTER LAUNCH:"; grep -m1 -E "Uncaught Kotlin exception|Fatal error" "$OUT" | tr -d '\r' | cut -c1-300
    kill "$PID" >/dev/null 2>&1 || true
    exit 1
fi
kill "$PID" >/dev/null 2>&1 || true
grep -o "UNIFIEDPREFS_STATE.*" "$OUT" | tail -1 | tr -d '\r' || { echo "no state line printed"; exit 1; }
