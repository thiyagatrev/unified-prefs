# Contracts: proven state after Wave 0

The contracts themselves (C1–C11) are in
`docs/superpowers/plans/2026-11-12-unified-prefs-poc-parallel.md`, section "Contracts".
That section has been corrected to match what Wave 0 proved. This file records the proof.

## Stubs and real code at tag `wave0`

| Contract | File | State |
|---|---|---|
| C1 keys | `storage/src/commonMain/.../PrefKeys.kt` | real |
| C2 factory, scope | `storage/src/commonMain/.../DataStoreFactory.kt` | real |
| C3 repository | `storage/src/commonMain/.../PrefsRepository.kt` | stub, WP-A |
| C4 Android wiring | `storage/src/androidMain/.../AndroidStorage.kt` | stub, WP-B |
| C5 iOS wiring | `storage/src/iosMain/.../IosStorage.kt` (`iosStorePath` real), `UserDefaultsMigration.kt`, `SwiftBridge.kt` | stub, WP-C |
| C6 shared UI | `shared-ui/src/commonMain/.../SharedSettingsScreen.kt`, `shared-ui/src/iosMain/.../MainViewController.kt` | stub, WP-D |
| test fake | `storage/src/commonTest/.../FakeDataStore.kt` | real |

A stub body is `TODO("WP-x")`, which throws `NotImplementedError` when called.

## Commands proven to pass

| Command | Result at `wave0` |
|---|---|
| `./gradlew :storage:jvmTest` | 1 test (`SmokeTest`) |
| `./gradlew :storage:testAndroidHostTest` | 2 tests (`AndroidSmokeTest`, and the common `SmokeTest`) |
| `./gradlew :storage:iosSimulatorArm64Test` | 2 tests (`IosSmokeTest`, and the common `SmokeTest`) |
| `./gradlew :shared-ui:compileAndroidMain :shared-ui:compileKotlinIosSimulatorArm64` | builds |
| `./gradlew :androidApp:assembleDebug` | builds |
| `scripts/ios-install.sh` | `BUILD SUCCEEDED`, framework `Shared` linked |
| `scripts/ios-launch.sh` | prints the placeholder app's `UNIFIEDPREFS_STATE` line |
| `scripts/android-install-before.sh`, `android-seed.sh`, `android-launch.sh`, `android-install.sh` | before build installs, seeds to `dark_mode=true`, `user_name=Asha`, `launch_count=3`; current build installs over it |

Common tests run on three hosts: JVM, Android host and iOS simulator. A common test added
by one package therefore also runs in the other two commands after merge.

## Facts a package needs

- Test source sets: `commonTest`, `androidHostTest`, `iosTest`.
- Android host tests use Robolectric 4.17 with `@Config(sdk = [35])` and JUnit 4 (`org.junit.Test`).
- A fresh worktree needs no `local.properties`; the Android SDK is found through `ANDROID_HOME`.
- iOS simulator: `iPhone 17` (iOS 26.5). Override with `IOS_SIM=<name>`.
- The iOS app's stdout reaches the host only through `scripts/ios-launch.sh`, and only if the app
  calls `setvbuf(stdout, nil, _IOLBF, 0)` before printing.
- Swift names, from the generated `Shared.h`: `IosStorage.shared.repo`, `PrefKeys.shared.darkMode`,
  `getBoolean(key:)`, `putBoolean(key:value:)`, `getInt(key:)` returning `Int32`,
  `watchBoolean(key:onChange:)` delivering `KotlinBoolean`, `watchInt` delivering `KotlinInt`,
  `watchString` delivering `String`, `MainViewControllerKt.MainViewController()`.
- `scripts/android-seed.sh` taps fixed coordinates on the Pixel 5 layout of the before-state app.
  Run it once, on a fresh install, with the app in front.

## Found in Wave 2

- The iOS simulator holds recent `UserDefaults` writes in memory and can lose them when
  `simctl install` replaces an app, because the data container moves. In three of three
  trials only the first key written was on disk before the install. `scripts/ios-install.sh`
  now restarts the simulator before installing, which forces the write. Without this the
  upgrade test fails intermittently for a reason unrelated to the migration code.
