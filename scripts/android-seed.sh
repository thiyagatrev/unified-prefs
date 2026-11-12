#!/bin/bash
# Run once on a fresh install with the app in front: turns the native dark-mode switch on and types Asha.
source "$(dirname "$0")/_common.sh"; require_android
"$ADB" shell input tap 964 345
"$ADB" shell input tap 540 543
sleep 1
"$ADB" shell input text Asha
sleep 1
