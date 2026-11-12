#!/bin/bash
# Installs the committed build of tag before-ios.
source "$(dirname "$0")/_common.sh"
TMP=$(mktemp -d)
unzip -q artifacts/before-ios.app.zip -d "$TMP"
boot_ios
xcrun simctl install "$IOS_SIM" "$TMP/iosApp.app"
