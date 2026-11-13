# Sourced by the other scripts.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
APP_ID=com.example.unifiedprefs
IOS_SIM="${IOS_SIM:-iPhone 17}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
require_android() {
    [ "$("$ADB" get-state 2>/dev/null)" = "device" ] || { echo "No Android emulator running. Start Pixel_5_API_35 first."; exit 1; }
}
boot_ios() {
    xcrun simctl boot "$IOS_SIM" 2>/dev/null || true
    # `simctl bootstatus` has been seen to hang for 15+ minutes on an already-booted device, so cap
    # it at 2 minutes and accept the device if simctl lists it as Booted.
    perl -e 'alarm shift; exec @ARGV' 120 xcrun simctl bootstatus "$IOS_SIM" >/dev/null 2>&1 ||
        xcrun simctl list devices booted | grep -q "$IOS_SIM (" ||
        { echo "simulator $IOS_SIM did not boot" >&2; return 1; }
}
