# Sourced by the other scripts.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
APP_ID=com.example.unifiedprefs
IOS_SIM="${IOS_SIM:-iPhone 17}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
require_android() {
    [ "$("$ADB" get-state 2>/dev/null)" = "device" ] || { echo "No Android emulator running. Start Pixel_5_API_35 first."; exit 1; }
}
boot_ios() { xcrun simctl boot "$IOS_SIM" 2>/dev/null || true; xcrun simctl bootstatus "$IOS_SIM" >/dev/null; }
