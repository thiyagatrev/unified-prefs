#!/bin/bash
source "$(dirname "$0")/_common.sh"
boot_ios
xcrun simctl terminate "$IOS_SIM" "$APP_ID" >/dev/null 2>&1 || true
xcrun simctl uninstall "$IOS_SIM" "$APP_ID" >/dev/null 2>&1 || true
