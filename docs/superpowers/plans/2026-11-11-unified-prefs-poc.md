# Unified Preferences POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that one KMP storage module backed by Jetpack DataStore can replace `SharedPreferences` (Android) and `NSUserDefaults` (iOS) underneath existing native prefs classes, without changing their call sites or losing existing users' data.

**Architecture:** A `:storage` KMP module owns a single DataStore instance, a typed key registry, and a `PrefsRepository` that serves synchronous reads from an in-memory copy and saves to DataStore in the background. Each native app keeps its existing `LegacyPrefs` class, whose internals are switched from the platform store to the repository. A `:shared-ui` Compose Multiplatform module reads and writes the same keys and is the single iOS framework, re-exporting `:storage`.

**Tech Stack:** Kotlin Multiplatform, Jetpack DataStore Preferences (KMP), kotlinx-coroutines, Compose Multiplatform, Jetpack Compose (Android host), SwiftUI (iOS host), XcodeGen for the Xcode project.

**Spec:** No separate spec file. The design was agreed in conversation and is restated in full in this plan. This is a spike: the output is `FINDINGS.md`, and the code is throwaway.

## Global Constraints

- Project root folder: `unified-prefs-poc/` (all paths below are relative to it).
- Package and Android `applicationId`: `com.example.unifiedprefs`. iOS bundle id: `com.example.unifiedprefs`.
- Android `minSdk` 24, `compileSdk`/`targetSdk` 35. iOS deployment target 15.0. JDK 17.
- Versions: take the mutually compatible set that the Kotlin Multiplatform wizard (kmp.jetbrains.com) currently emits and pin it in `gradle/libs.versions.toml`. Floors: Kotlin 2.2.20, Compose Multiplatform 1.9.0, DataStore 1.1.7, kotlinx-coroutines 1.10.2. Verify each against Maven Central before pinning; these floors are from memory.
- iOS targets: `iosArm64` and `iosSimulatorArm64` only.
- Exactly one DataStore instance per process. No code outside `:storage` may construct a DataStore or a key.
- Legacy Android prefs file name: `app_prefs`. DataStore file name: `app.preferences_pb`.
- All names, keys and sample values are invented. Nothing from any work project.
- Out of scope: encryption, widgets and app groups, lint rules, SKIE, release signing.

## Keys

| Registry property | Stored name | Type | Default | Android legacy key | iOS legacy key |
|---|---|---|---|---|---|
| `PrefKeys.darkMode` | `dark_mode` | Boolean | `false` | `dark_mode` | `isDarkMode` |
| `PrefKeys.launchCount` | `launch_count` | Int | `0` | `launch_count` | `launchCount` |
| `PrefKeys.userName` | `user_name` | String | `""` | `user_name` | `userName` |

Stored names equal the Android legacy names so `SharedPreferencesMigration` needs no mapping. The iOS migration maps the iOS legacy names onto them.

## File Structure

```
unified-prefs-poc/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  storage/                                  KMP library
    src/commonMain/kotlin/com/example/unifiedprefs/storage/
      PrefKeys.kt                           key classes + registry
      PrefsRepository.kt                    in-memory reads, background saves, Flows
      DataStoreFactory.kt                   createDataStore(migrations, producePath)
    src/androidMain/kotlin/.../AndroidStorage.kt     singleton + SharedPreferencesMigration
    src/iosMain/kotlin/.../IosStorage.kt             singleton + file path
    src/iosMain/kotlin/.../UserDefaultsMigration.kt  NSUserDefaults -> DataStore
    src/iosMain/kotlin/.../SwiftBridge.kt            Cancellable + watch* callbacks
    src/commonTest/kotlin/.../FakeDataStore.kt, PrefsRepositoryTest.kt, PrefKeysTest.kt
    src/iosTest/kotlin/.../UserDefaultsMigrationTest.kt
  shared-ui/                                KMP + Compose Multiplatform, iOS framework "Shared"
    src/commonMain/kotlin/.../SharedSettingsScreen.kt
    src/iosMain/kotlin/.../MainViewController.kt
  androidApp/                               native Android host
    src/main/kotlin/.../LegacyPrefs.kt, MainActivity.kt, PocApp.kt
  iosApp/                                   native iOS host
    project.yml, iosApp/LegacyPrefs.swift, ContentView.swift, PocApp.swift, ComposeView.swift
  FINDINGS.md
```

## Review Focus

1. **First launch with no legacy data:** every getter returns the key's default and nothing crashes.
2. **Legacy key absent or holding an unexpected value on iOS:** the migration skips absent keys, and an integer outside `Int` range is clamped rather than crashing.
3. **Migration runs once:** a value changed after migration is not overwritten by the old legacy value on the next launch. Android guarantees this by deleting the legacy keys; iOS by a marker key.
4. **Rapid successive writes to one key:** the value persisted on disk equals the last write.
5. **Singleton accessor called twice:** it returns the same repository and never constructs a second DataStore on the same file.

Each of these has a test in the task that owns the code (Tasks 2, 3 and 6).

---

## Phase A: no Xcode needed

### Task 1: Scaffold and the Android "before" app

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper
- Create: `androidApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/kotlin/com/example/unifiedprefs/LegacyPrefs.kt`, `PocApp.kt`, `MainActivity.kt`

**Interfaces:**
- Produces: `class LegacyPrefs(context: Context)` with `var darkMode: Boolean`, `var launchCount: Int`, `var userName: String`. This public surface must not change in any later task.

- [ ] **Step 1:** `git init` in `unified-prefs-poc/`, add a `.gitignore` for Gradle, Android Studio and Xcode output, and create the Gradle root with modules `:androidApp`, `:storage`, `:shared-ui` declared (the last two as empty KMP library modules that build).
- [ ] **Step 2:** Implement `LegacyPrefs` directly over `context.getSharedPreferences("app_prefs", MODE_PRIVATE)` using the Android legacy keys from the Keys table and `apply()` for writes.
- [ ] **Step 3:** In `PocApp.onCreate`, synchronously run `prefs.launchCount = prefs.launchCount + 1` before any UI. `MainActivity` shows a Jetpack Compose section titled "Native (LegacyPrefs)" with a dark-mode switch, a user-name text field, and the launch count as text.
- [ ] **Step 4: Verify.** Run `./gradlew :androidApp:installDebug` on the `Pixel_5_API_35` emulator. Expected: app launches, count shows 1, and after force-stop and relaunch shows 2 with switch and name retained.
- [ ] **Step 5: Commit and tag.** `git commit -m "chore: scaffold and Android before-state app"` then `git tag before-android`.

### Task 2: Key registry and repository (common code)

**Files:**
- Create: `storage/build.gradle.kts` (targets `androidTarget`, `iosArm64`, `iosSimulatorArm64`; `api` on datastore-preferences-core and coroutines-core)
- Create: `storage/src/commonMain/.../PrefKeys.kt`, `PrefsRepository.kt`, `DataStoreFactory.kt`
- Test: `storage/src/commonTest/.../FakeDataStore.kt`, `PrefsRepositoryTest.kt`, `PrefKeysTest.kt`

**Interfaces:**
- Produces:
  - `class BoolKey internal constructor(name: String, val default: Boolean)`, `class IntKey internal constructor(name: String, val default: Int)`, `class StringKey internal constructor(name: String, val default: String)`, each with `val name: String` and an `internal val key: Preferences.Key<T>`.
  - `object PrefKeys { val darkMode: BoolKey; val launchCount: IntKey; val userName: StringKey; val allNames: List<String> }`
  - `class PrefsRepository internal constructor(dataStore: DataStore<Preferences>, scope: CoroutineScope)`
  - `fun getBoolean(key: BoolKey): Boolean`, `fun putBoolean(key: BoolKey, value: Boolean)`, and the same pair for `Int`/`IntKey` and `String`/`StringKey`
  - `fun observeBoolean(key: BoolKey): Flow<Boolean>`, `observeInt`, `observeString`
  - `suspend fun flush()` which returns once every write made so far is on disk
  - `fun createDataStore(migrations: List<DataMigration<Preferences>>, producePath: () -> String): DataStore<Preferences>`

- [ ] **Step 1: Write the failing tests.** `FakeDataStore` is an in-memory `DataStore<Preferences>` over a `MutableStateFlow`, optionally seeded. Tests, using `runTest` and a `StandardTestDispatcher`-backed scope:

```kotlin
@Test fun returnsDefaultsWhenStoreIsEmpty()          // false, 0, "" for the three keys
@Test fun readsSeededValuesSynchronouslyAfterConstruction()
@Test fun putIsVisibleToGetImmediately()             // putInt(k, 5); assertEquals(5, getInt(k)) with no advance
@Test fun putReachesDataStoreAfterFlush()            // fake.data.first()[k.key] == 5 after flush()
@Test fun lastOfRapidWritesWins()                    // putInt 1..100, flush(), store holds 100
@Test fun observeEmitsCurrentThenChangesAndSkipsDuplicates() // Turbine: false, true; a second put(true) emits nothing
// PrefKeysTest
@Test fun allStoredNamesAreUnique()                  // PrefKeys.allNames.toSet().size == PrefKeys.allNames.size
@Test fun storedNamesMatchTheKeysTable()             // "dark_mode", "launch_count", "user_name"
```

- [ ] **Step 2:** Run `./gradlew :storage:testDebugUnitTest`. Expected: compilation fails because the types do not exist.
- [ ] **Step 3:** Implement the three files. The repository loads the initial snapshot with a blocking read in its constructor, holds it in a `MutableStateFlow<Preferences>`, updates that flow synchronously on every put, and launches the DataStore `edit` on a single-parallelism dispatcher so saves keep write order. Each key class appends its name to `PrefKeys.allNames` on construction.
- [ ] **Step 4:** Run `./gradlew :storage:testDebugUnitTest`. Expected: all 8 tests pass.
- [ ] **Step 5: Commit.** `git commit -m "feat(storage): key registry and PrefsRepository"`

### Task 3: Android wiring and migration

**Files:**
- Create: `storage/src/androidMain/.../AndroidStorage.kt`
- Modify: `androidApp/src/main/kotlin/.../LegacyPrefs.kt` (internals only)
- Test: `storage/src/androidUnitTest/.../AndroidStorageTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `createDataStore`, `PrefsRepository`, `PrefKeys` from Task 2.
- Produces: `fun prefsRepository(context: Context): PrefsRepository`, a process-wide singleton. The DataStore file is `context.filesDir/datastore/app.preferences_pb` and its migrations are `listOf(SharedPreferencesMigration(context, "app_prefs"))`.
- Produces: `internal fun resetStorageForTest()`, which cancels the current repository's scope, clears the singleton and deletes the DataStore file.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun accessorReturnsSameInstanceOnEveryCall()   // assertSame(prefsRepository(ctx), prefsRepository(ctx))
@Test fun migratesExistingSharedPreferences()        // seed app_prefs: dark_mode=true, launch_count=3, user_name="Asha"; repo returns the same three
@Test fun migratedKeysAreRemovedFromSharedPreferences() // after migration and flush(), app_prefs contains none of the three keys
@Test fun emptySharedPreferencesYieldsDefaults()
```

`SharedPreferencesMigration` re-runs whenever its keys are still present in `SharedPreferences`, so removal of the legacy keys is what makes the Android migration run only once. The third test pins that. Each test calls `resetStorageForTest()` first so it gets a fresh singleton and file.

- [ ] **Step 2:** Run `./gradlew :storage:testDebugUnitTest`. Expected: the new tests fail to compile.
- [ ] **Step 3:** Implement `AndroidStorage.kt` with a `@Volatile` instance and double-checked locking, using `context.applicationContext`.
- [ ] **Step 4:** Rewrite the inside of `LegacyPrefs` to delegate each property to `prefsRepository(context)` with the matching `PrefKeys` entry. No public signature changes, and no edits to `PocApp` or `MainActivity`.
- [ ] **Step 5:** Run `./gradlew :storage:testDebugUnitTest`. Expected: all pass.
- [ ] **Step 6: Verify the upgrade path on the emulator.**
  1. Uninstall the app. `git stash -u` if needed, `git checkout before-android`, `./gradlew :androidApp:installDebug`.
  2. Launch three times; set dark mode on and the name to `Asha`. Screen shows count 3.
  3. `git checkout -` then `./gradlew :androidApp:installDebug` (installs over the top).
  4. Launch. Expected: dark mode on, name `Asha`, count 4.
  5. Run `adb shell run-as com.example.unifiedprefs ls files/datastore`. Expected: `app.preferences_pb`.
  6. Run `adb shell run-as com.example.unifiedprefs ls shared_prefs`. Expected: `app_prefs.xml` is gone or holds none of the three keys. This means Android keeps no rollback copy, unlike iOS; note it in the findings.
- [ ] **Step 7: Commit.** `git commit -m "feat(android): LegacyPrefs delegates to unified storage"`

### Task 4: Shared Compose Multiplatform screen

**Files:**
- Create: `shared-ui/build.gradle.kts` (Compose Multiplatform; `api(project(":storage"))`; iOS framework `baseName = "Shared"`, `isStatic = true`, `export(project(":storage"))`)
- Create: `shared-ui/src/commonMain/.../SharedSettingsScreen.kt`
- Modify: `androidApp/.../MainActivity.kt`, `androidApp/build.gradle.kts`

**Interfaces:**
- Consumes: `PrefsRepository`, `PrefKeys`.
- Produces: `@Composable fun SharedSettingsScreen(repo: PrefsRepository)` showing a section titled "Shared (CMP)" with the same switch, text field and count, driven by `observeBoolean`/`observeString`/`observeInt` and writing through the `put*` methods.

- [ ] **Step 1:** Implement `SharedSettingsScreen`.
- [ ] **Step 2:** In `MainActivity`, render the native section and `SharedSettingsScreen(prefsRepository(this))` one above the other. Change the native section to collect `observe*` Flows so it reacts to changes made in the shared section.
- [ ] **Step 3: Verify.** `./gradlew :androidApp:installDebug`. Expected: toggling the switch or editing the name in either section updates the other within the same frame or two, and both survive a force-stop and relaunch.
- [ ] **Step 4: Commit.** `git commit -m "feat(shared-ui): CMP settings screen on the unified store"`

---

## Phase B: needs Xcode

Before starting: Xcode installed, `sudo xcode-select -s /Applications/Xcode.app` run by the user, a simulator runtime downloaded, and `xcodegen` available (`brew install xcodegen`).

### Task 5: iOS "before" app

**Files:**
- Create: `iosApp/project.yml`, `iosApp/iosApp/PocApp.swift`, `ContentView.swift`, `LegacyPrefs.swift`

**Interfaces:**
- Produces: `final class LegacyPrefs { static let shared: LegacyPrefs; var isDarkMode: Bool; var launchCount: Int; var userName: String }`. This public surface must not change in Task 7.

- [ ] **Step 1:** Write `project.yml` for an iOS 15 SwiftUI app target `iosApp` with bundle id `com.example.unifiedprefs`, and run `xcodegen generate` in `iosApp/`.
- [ ] **Step 2:** Implement `LegacyPrefs` over `UserDefaults.standard` with the iOS legacy keys from the Keys table.
- [ ] **Step 3:** In `PocApp.init`, synchronously increment `launchCount`. `ContentView` shows a section titled "Native (LegacyPrefs)" with the same three controls as Android.
- [ ] **Step 4: Verify.** Build and run on an iPhone simulator with `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=<installed iPhone>' build`, then install and launch with `xcrun simctl`. Expected: count 1, then 2 after terminate and relaunch, with switch and name retained.
- [ ] **Step 5: Commit and tag.** `git commit -m "chore: iOS before-state app"` then `git tag before-ios`.

### Task 6: iOS storage wiring, migration and Swift bridge

**Files:**
- Create: `storage/src/iosMain/.../IosStorage.kt`, `UserDefaultsMigration.kt`, `SwiftBridge.kt`
- Test: `storage/src/iosTest/.../UserDefaultsMigrationTest.kt`

**Interfaces:**
- Consumes: `createDataStore`, `PrefsRepository`, key classes.
- Produces:
  - `object IosStorage { val repo: PrefsRepository }` (Swift: `IosStorage.shared.repo`). File path: Application Support directory, created if missing, plus `/app.preferences_pb`.
  - `class UserDefaultsMigration(defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : DataMigration<Preferences>`, guarded by a boolean marker key `__ud_migrated`; `cleanUp()` leaves the legacy values in place.
  - `fun interface Cancellable { fun cancel() }`
  - `fun PrefsRepository.watchBoolean(key: BoolKey, onChange: (Boolean) -> Unit): Cancellable`, plus `watchInt` and `watchString`. Callbacks are delivered on the main thread.

- [ ] **Step 1: Write the failing tests**, each using a fresh `NSUserDefaults(suiteName:)` that is removed afterwards:

```kotlin
@Test fun mapsIosLegacyKeysToStoredNames()     // isDarkMode=true, launchCount=3, userName="Asha" -> dark_mode, launch_count, user_name
@Test fun skipsAbsentKeys()                    // only userName set -> dark_mode and launch_count are absent from the result
@Test fun clampsIntegerOutsideIntRange()       // launchCount = 5_000_000_000 -> Int.MAX_VALUE
@Test fun shouldMigrateIsFalseOnceMarkerIsSet()
@Test fun leavesLegacyValuesInPlace()          // after migrate + cleanUp, defaults still returns userName "Asha"
```

- [ ] **Step 2:** Run `./gradlew :storage:iosSimulatorArm64Test`. Expected: compilation fails.
- [ ] **Step 3:** Implement the three files.
- [ ] **Step 4:** Run `./gradlew :storage:iosSimulatorArm64Test`. Expected: the 5 new tests and the 8 common tests pass.
- [ ] **Step 5: Commit.** `git commit -m "feat(ios): storage singleton, NSUserDefaults migration, Swift bridge"`

### Task 7: iOS switch-over and shared screen

**Files:**
- Create: `shared-ui/src/iosMain/.../MainViewController.kt`, `iosApp/iosApp/ComposeView.swift`
- Modify: `iosApp/project.yml`, `iosApp/iosApp/LegacyPrefs.swift` (internals only), `ContentView.swift`

**Interfaces:**
- Consumes: `IosStorage`, `watch*`, `SharedSettingsScreen`.
- Produces: `fun MainViewController(): UIViewController` returning `ComposeUIViewController { SharedSettingsScreen(IosStorage.repo) }`.

- [ ] **Step 1:** Add a pre-build script phase to `project.yml` that runs `./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode` from the project root, add the framework search path, and regenerate with `xcodegen generate`.
- [ ] **Step 2:** Rewrite the inside of `LegacyPrefs.swift` to delegate to `IosStorage.shared.repo` with `PrefKeys.shared` entries. No public signature changes, and no edits to `PocApp.swift`.
- [ ] **Step 3:** In `ContentView`, drive the native section from `watch*` callbacks (cancelled on disappear) and add the Compose section below it through a `UIViewControllerRepresentable` wrapping `MainViewController()`.
- [ ] **Step 4: Verify the upgrade path on the simulator.**
  1. Delete the app from the simulator. `git checkout before-ios`, build and install.
  2. Launch three times; set dark mode on and the name to `Asha`. Screen shows count 3.
  3. `git checkout -`, build and install over the top.
  4. Launch. Expected: dark mode on, name `Asha`, count 4, with no crash at start. This is the check for synchronous reads during `PocApp.init`.
  5. Toggle in the native section and confirm the Compose section follows, then the reverse.
  6. Terminate and relaunch. Expected: values retained and count 5.
- [ ] **Step 5: Commit.** `git commit -m "feat(ios): LegacyPrefs delegates to unified storage; embed CMP screen"`

### Task 8: Findings

**Files:**
- Create: `FINDINGS.md`

- [ ] **Step 1:** Measure the blocking first load on each platform: log the elapsed time around first access to the repository in `PocApp`, over five cold starts each, and record the median.
- [ ] **Step 2:** Write `FINDINGS.md` with: the answer to the POC question (yes, no, or yes with conditions); the result of every verification step in Tasks 3, 4 and 7; the load timings; whether the legacy stores keep a rollback copy on each platform; every problem hit and how it was resolved; and what the POC did not cover (encryption, widgets, large data, real-device runs).
- [ ] **Step 3: Commit.** `git commit -m "docs: POC findings"`
