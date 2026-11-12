#!/bin/bash
# Generates the Xcode project, builds for the simulator and installs over any existing install.
source "$(dirname "$0")/_common.sh"
(cd iosApp && xcodegen generate --quiet)
boot_ios
if xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator \
    -destination "platform=iOS Simulator,name=$IOS_SIM" -derivedDataPath iosApp/build build > ios-build.log 2>&1; then
    echo "BUILD SUCCEEDED"
else
    grep -E "error:|^e: |What went wrong" -A2 ios-build.log | head -40; exit 1
fi
# The simulator keeps recent UserDefaults writes in memory and loses them when an install
# moves the app's data container. A clean restart forces them to disk first. Whether a real
# device can lose them the same way during an app update has not been tested.
xcrun simctl shutdown "$IOS_SIM" >/dev/null 2>&1 || true
boot_ios
xcrun simctl install "$IOS_SIM" iosApp/build/Build/Products/Debug-iphonesimulator/iosApp.app
