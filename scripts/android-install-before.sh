#!/bin/bash
# Installs the committed build of tag before-android.
source "$(dirname "$0")/_common.sh"; require_android
"$ADB" install -r artifacts/before-android.apk | tail -1
