#!/bin/bash
# Builds androidApp (debug) and installs it over any existing install.
source "$(dirname "$0")/_common.sh"; require_android
./gradlew :androidApp:installDebug -q > android-install.log 2>&1 && echo "BUILD SUCCESSFUL" || { grep -E "^e: |error:|What went wrong|FAILED" -A3 android-install.log | head -30; exit 1; }
