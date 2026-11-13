#!/bin/bash
# One full real-app-scale run: old build + generated old store -> upgrade -> migration run ->
# RUNS cold starts in each mode, alternating. Writes results/<platform>-<profile>.csv.
# Usage: bench.sh <android|ios> <typical|heavy> [runs=11]
# RUNS is odd by default so each median is a launch that really happened.
# Fails if the migration run's checksum differs from the generator's expected value.
source "$(dirname "$0")/_common.sh"
PLATFORM="${1:?android or ios}"
PROFILE="${2:?typical or heavy}"
RUNS="${3:-11}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

python3 -I tools/genprefs.py --profile "$PROFILE" --out build/genprefs >/dev/null
EXPECTED="$(tr -d '[:space:]' < "build/genprefs/$PROFILE/expected-$PLATFORM.txt")"
mkdir -p results
CSV="results/$PLATFORM-$PROFILE.csv"
echo "platform,profile,run,host_load1,mode,load_ms,keys_ms,checksum,save_ms,burst_ms,burst_saves" > "$CSV"

# ---- platform launchers: each prints one UNIFIEDPREFS_BENCH line or fails ----
android_launch() { # $1 = datastore | baseline
    "$ADB" shell am force-stop "$APP_ID"
    "$ADB" shell "run-as $APP_ID sh -c 'mkdir -p files; echo $1 > files/bench_mode'"
    "$ADB" logcat -c
    "$ADB" shell am start -W -n "$APP_ID/.MainActivity" >/dev/null
    for _ in $(seq 60); do
        line="$("$ADB" logcat -d -s UnifiedPrefs:I | grep -o 'UNIFIEDPREFS_BENCH.*' | tail -1 || true)"
        [ -n "$line" ] && { echo "$line"; return 0; }
        "$ADB" logcat -d | grep -q "FATAL EXCEPTION" && { echo "app crashed" >&2; return 1; }
        sleep 0.5
    done
    echo "no BENCH line within 30 s" >&2; return 1
}

ios_launch() { # $1 = datastore | baseline
    xcrun simctl terminate "$IOS_SIM" "$APP_ID" 2>/dev/null || true
    local out="$TMP/ios-$1.log"; : > "$out"
    xcrun simctl launch --console-pty "$IOS_SIM" "$APP_ID" --bench "$1" > "$out" 2>&1 &
    local pid=$!
    for _ in $(seq 60); do
        line="$(grep -o 'UNIFIEDPREFS_BENCH.*' "$out" | tr -d '\r' | tail -1 || true)"
        if [ -n "$line" ]; then kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; echo "$line"; return 0; fi
        grep -qE "Uncaught Kotlin exception|Fatal error" "$out" && { kill "$pid" 2>/dev/null; cat "$out" >&2; return 1; }
        sleep 0.5
    done
    kill "$pid" 2>/dev/null; echo "no BENCH line within 30 s" >&2; return 1
}

record() { # $1 = run label, $2 = requested mode, $3 = BENCH line
    # A line from the wrong mode means the mode switch failed; never file it under the asked-for mode.
    echo "$3" | grep -q " mode=$2 " || { echo "asked for mode=$2, got: $3" >&2; exit 1; }
    local load; load="$(sysctl -n vm.loadavg | awk '{print $2}')"
    echo "$3" | tr ' ' '\n' | awk -F= -v p="$PLATFORM" -v pr="$PROFILE" -v run="$1" -v load="$load" '
        NF == 2 { v[$1] = $2 }
        END { printf "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", p, pr, run, load, v["mode"], v["load_ms"], v["keys_ms"],
              v["checksum"], v["save_ms"], v["burst_ms"], v["burst_saves"] }' >> "$CSV"
    echo "  $1 (load $load): $3"
}

check_checksum() { # $1 = BENCH line
    local got; got="$(echo "$1" | grep -o 'checksum=[0-9a-f]*' | cut -d= -f2)"
    [ "$got" = "$EXPECTED" ] || { echo "CHECKSUM MISMATCH: got $got, expected $EXPECTED" >&2; exit 1; }
}

# ---- install old build, seed, upgrade ----
echo "== $PLATFORM $PROFILE: install old build, seed, upgrade"
if [ "$PLATFORM" = android ]; then
    require_android
    "$ADB" uninstall "$APP_ID" >/dev/null 2>&1 || true
    scripts/android-install-before.sh >/dev/null
    scripts/seed-legacy.sh android "$PROFILE"
    # Launch the old build once so Android rewrites app_prefs.xml in its own format, as a real
    # user's file would be. The baseline copy is taken from that rewrite.
    "$ADB" shell am start -W -n "$APP_ID/.MainActivity" >/dev/null; sleep 4
    "$ADB" shell am force-stop "$APP_ID"
    "$ADB" shell run-as "$APP_ID" cp shared_prefs/app_prefs.xml shared_prefs/baseline_prefs.xml
    echo "old build rewrote app_prefs.xml: $("$ADB" shell run-as "$APP_ID" stat -c %s shared_prefs/app_prefs.xml | tr -d '\r') B"
    scripts/android-install.sh >/dev/null
    LAUNCH=android_launch
else
    scripts/ios-uninstall.sh >/dev/null 2>&1 || true
    scripts/ios-install-before.sh > "$TMP/install-before.log" 2>&1
    scripts/seed-legacy.sh ios "$PROFILE"
    # Launch the old build once so cfprefsd rewrites the plist itself; copy that as the baseline.
    xcrun simctl launch "$IOS_SIM" "$APP_ID" >/dev/null; sleep 5
    xcrun simctl terminate "$IOS_SIM" "$APP_ID" 2>/dev/null || true
    data="$(xcrun simctl get_app_container "$IOS_SIM" "$APP_ID" data)"
    xcrun simctl shutdown "$IOS_SIM"   # forces cfprefsd to write the plist out
    cp "$data/Library/Preferences/$APP_ID.plist" "$data/Library/Preferences/baseline.plist"
    echo "old build rewrote the plist: $(stat -f %z "$data/Library/Preferences/$APP_ID.plist") B"
    boot_ios
    scripts/ios-install.sh > "$TMP/install.log" 2>&1 || { tail -20 "$TMP/install.log"; exit 1; }
    LAUNCH=ios_launch
fi

# ---- runs ----
# Installing (an Xcode build, a simulator restart) loads the host heavily for minutes; measuring
# then mixes host noise into every column. Wait until the 1-minute load average is below 12.
for _ in $(seq 120); do
    awk -v l="$(sysctl -n vm.loadavg | awk '{print $2}')" 'BEGIN { exit !(l < 12) }' && break
    sleep 5
done
echo "host load before runs: $(sysctl -n vm.loadavg)"
line="$($LAUNCH datastore)"; record migration datastore "$line"; check_checksum "$line"
for i in $(seq "$RUNS"); do
    line="$($LAUNCH datastore)"; record "$i" datastore "$line"; check_checksum "$line"
    line="$($LAUNCH baseline)"; record "$i" baseline "$line"
done

# ---- file sizes ----
if [ "$PLATFORM" = android ]; then
    new="$("$ADB" shell run-as "$APP_ID" stat -c %s files/datastore/app.preferences_pb | tr -d '\r')"
    old="$("$ADB" shell run-as "$APP_ID" stat -c %s shared_prefs/baseline_prefs.xml | tr -d '\r')"
    "$ADB" shell "run-as $APP_ID rm -f files/bench_mode"
else
    xcrun simctl terminate "$IOS_SIM" "$APP_ID" 2>/dev/null || true
    data="$(xcrun simctl get_app_container "$IOS_SIM" "$APP_ID" data)"
    new="$(stat -f %z "$(find "$data/Library/Application Support" -name app.preferences_pb | head -1)")"
    old="$(stat -f %z "$data/Library/Preferences/baseline.plist")"
fi
echo "$PLATFORM,$PROFILE,new_store_bytes=$new,old_store_bytes=$old" > "results/$PLATFORM-$PROFILE-sizes.txt"
echo "== done: $CSV (new store $new B, old store $old B), checksum $EXPECTED matched"
