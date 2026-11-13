# UnifiedPrefs

A proof of concept: one Kotlin Multiplatform storage module, backed by Jetpack DataStore, sitting
underneath an existing native Android app (`SharedPreferences`) and an existing native iOS app
(`NSUserDefaults`), plus a shared Compose Multiplatform screen. Existing native preference classes
keep their public API; only their insides change. Old values are migrated once on first launch.

Results, including measurements with 200 preferences against the old stores, are in
[FINDINGS.md](FINDINGS.md).

## Layout

| Path | What it is |
|---|---|
| `storage/` | KMP module: `PrefKeys` (typed key registry, constructors `internal`), `GeneratedKeys` (200 generated keys), `PrefsRepository` (synchronous reads from memory, background saves), Android and iOS wiring, the iOS `NSUserDefaults` migration, checksum and benchmark routine |
| `shared-ui/` | Compose Multiplatform settings screen; also the iOS framework `Shared`, which re-exports `:storage` |
| `androidApp/` | Native Android app; `LegacyPrefs` delegates to the repository |
| `iosApp/` | Native SwiftUI app (XcodeGen project); `LegacyPrefs.swift` delegates to the repository |
| `tools/genprefs.py` | Generates the 200 keys and the old-store fixtures (`--self-check` runs its 15 checks) |
| `scripts/` | Install, seed, launch and benchmark scripts for the Android emulator and iOS simulator |
| `results/` | Raw benchmark data behind FINDINGS |
| `artifacts/` | The "before" builds of both apps, used to test upgrades |
| `docs/` | Design spec, implementation plans, proven contracts |

## Requirements

JDK 17, Android SDK (`ANDROID_HOME`), an emulator named `Pixel_5_API_35`, Xcode 26 with an
`iPhone 17` simulator, XcodeGen, Python 3.

Versions: Kotlin 2.4.10, AGP 9.1.1, Compose Multiplatform 1.11.1, DataStore 1.3.0, Gradle 9.6.1.

## Run

```bash
./gradlew :storage:jvmTest :storage:testAndroidHostTest :storage:iosSimulatorArm64Test
```

```bash
scripts/android-install.sh && scripts/android-launch.sh
```

```bash
scripts/ios-install.sh && scripts/ios-launch.sh
```

Benchmark one platform and profile (installs the old build, seeds 200 keys, upgrades, measures):

```bash
scripts/bench.sh android typical
```

```bash
python3 -I tools/bench_summary.py
```

## Status

A proof of concept, measured on an emulator and a simulator with Debug builds. Not tested on
physical devices or with Release builds. The benchmark code (`PrefsBench`, the two bench keys, the
`bench_mode` check) lives in production code only to measure the sample.
