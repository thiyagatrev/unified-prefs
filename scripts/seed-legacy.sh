#!/bin/bash
# Writes a generated old store into the installed before-state app, plus a baseline copy.
# Usage: seed-legacy.sh <android|ios> <profile | directory holding app_prefs.xml and legacy.plist>
# Contract R8. The app must be installed; it is stopped first.
source "$(dirname "$0")/_common.sh"
PLATFORM="${1:?android or ios}"
SRC="${2:?profile or directory}"
[ -d "$SRC" ] || SRC="build/genprefs/$SRC"
[ -d "$SRC" ] || { echo "No generated data at $SRC. Run tools/genprefs.py --profile first."; exit 1; }

case "$PLATFORM" in
android)
    require_android
    XML="$SRC/app_prefs.xml"
    "$ADB" shell am force-stop "$APP_ID"
    "$ADB" shell run-as "$APP_ID" mkdir -p shared_prefs
    for name in app_prefs baseline_prefs; do
        "$ADB" exec-in run-as "$APP_ID" sh -c "cat > shared_prefs/$name.xml" < "$XML"
    done
    echo "seeded android from $XML ($(wc -c < "$XML" | tr -d ' ') bytes)"
    ;;
ios)
    # Proven in Wave 0: `simctl spawn defaults import` writes the simulator's home domain, which
    # outlives an uninstall. Writing into the app's own container with the simulator shut down
    # matches a real device, and cfprefsd reads the file fresh on boot.
    PLIST="$SRC/legacy.plist"
    boot_ios
    DATA="$(xcrun simctl get_app_container "$IOS_SIM" "$APP_ID" data)"
    xcrun simctl terminate "$IOS_SIM" "$APP_ID" 2>/dev/null || true
    xcrun simctl shutdown "$IOS_SIM"
    mkdir -p "$DATA/Library/Preferences"
    cp "$PLIST" "$DATA/Library/Preferences/$APP_ID.plist"
    cp "$PLIST" "$DATA/Library/Preferences/baseline.plist"
    boot_ios
    echo "seeded ios from $PLIST ($(wc -c < "$PLIST" | tr -d ' ') bytes)"
    ;;
*) echo "first argument must be android or ios"; exit 1 ;;
esac
