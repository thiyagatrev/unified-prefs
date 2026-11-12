#!/bin/bash
source "$(dirname "$0")/_common.sh"; require_android
"$ADB" uninstall "$APP_ID" >/dev/null 2>&1 || true
