# UnifiedPrefs POC: Parallel Plan With Contracts

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run this plan. Each work package (WP) is one agent in its own git worktree. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Prove that one KMP storage module backed by Jetpack DataStore can replace `SharedPreferences` and `NSUserDefaults` underneath existing native prefs classes, without changing their call sites or losing existing users' data.

**Architecture:** `:storage` (KMP) owns one DataStore, a typed key registry and `PrefsRepository` (synchronous reads from memory, background saves). `:shared-ui` (Compose Multiplatform) is the single iOS framework `Shared` and re-exports `:storage`. `androidApp` and `iosApp` keep their `LegacyPrefs` classes and switch only the internals.

**Supersedes:** Tasks 2–8 of `2026-11-11-unified-prefs-poc.md`. Task 1 of that plan is done (commit `8f76c6f`, tag `before-android`).

**Spec:** none; the design is restated here. This is a spike and the code is throwaway. The output is `FINDINGS.md`.

## How this plan is meant to be run

- **Wave 0 is sequential and is done by the main session on a capable model.** It settles every build-system question, writes every signature as a compiling stub, and proves each test harness with a smoke test. After Wave 0, no work package needs to make a design or build decision.
- **Waves 1 and 2 are parallel and written for lightweight models.** Each package has exclusive files, fixed signatures, named tests with their assertions, and exact commands with expected output.
- **Wave 3 is the main session again:** interactive checks, measurements, findings, final review.

```
Wave 0  WP-0  contracts, build config, stubs, harnesses        (main session)
Wave 1  WP-A  PrefsRepository            ┐
        WP-B  Android storage wiring     │
        WP-C  iOS storage + migration    ├ parallel, max 3 at once
        WP-D  shared Compose screen      │
        WP-E  iOS before-state app       ┘
Wave 2  WP-F  Android switch-over        ┐ parallel
        WP-G  iOS switch-over            ┘
Wave 3  WP-H  integration checks, timings, FINDINGS.md          (main session)
```

**Concurrency cap: 3 packages at once.** Each runs its own Gradle daemon and Kotlin/Native compile; five at once will exhaust memory. Suggested order for Wave 1: A, B, E first; then C and D.

## Rules for every work package

1. Work in your own worktree on branch `wp/<id>`, cut from the wave's base commit named by the dispatcher.
2. Touch only the files listed under **Owns**. Never edit any `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `iosApp/project.yml`, `scripts/`, or another package's files.
3. Do not change any signature in the Contracts section. If a contract looks wrong or a dependency is missing, stop and report `BLOCKED` with the reason. Do not work around it.
4. Test first: write the named tests, run them and see them fail, then implement, then see them pass.
5. Commit on your branch with the message given. Do not merge, push or tag unless your package says to.
6. End with this report and nothing else:

```
STATUS: DONE | BLOCKED
PACKAGE: WP-<id>
COMMIT: <short sha>
COMMANDS: <each command run, with its last result line>
FILES: <files created or changed>
DEVIATIONS: <none, or what and why>
```

Intended model: Haiku-class. If a package reports `BLOCKED` twice, re-dispatch it on Sonnet-class.

## Global constraints

- Package and `applicationId`: `com.example.unifiedprefs`. iOS bundle id: `com.example.unifiedprefs`.
- Versions are pinned in `gradle/libs.versions.toml` (AGP 9.1.1, Kotlin 2.4.10, Compose Multiplatform 1.11.1, DataStore 1.3.0, coroutines 1.11.0, Gradle 9.6.1). `minSdk` 24, `compileSdk` 36, iOS 15.0, JDK 17.
- Exactly one DataStore instance per process. Nothing outside `:storage` constructs a DataStore or a key.
- Legacy Android prefs file: `app_prefs`. DataStore file: `app.preferences_pb`.
- Android device: emulator `Pixel_5_API_35`. iOS device: simulator `iPhone 16`.
- Out of scope: encryption, widgets, lint rules, SKIE.

## Keys

| Registry property | Stored name | Type | Default | Android legacy key | iOS legacy key |
|---|---|---|---|---|---|
| `PrefKeys.darkMode` | `dark_mode` | Boolean | `false` | `dark_mode` | `isDarkMode` |
| `PrefKeys.launchCount` | `launch_count` | Int | `0` | `launch_count` | `launchCount` |
| `PrefKeys.userName` | `user_name` | String | `""` | `user_name` | `userName` |

---

## Contracts

All Kotlin below is in package `com.example.unifiedprefs.storage` unless stated. Wave 0 commits every one of these, either as real code (marked REAL) or as a stub whose bodies are `TODO("WP-x")` (marked STUB). `docs/CONTRACTS.md`, written by Wave 0, repeats this section with the commands as actually proven; where the two differ, `docs/CONTRACTS.md` wins.

### C1. Keys — `storage/src/commonMain/.../PrefKeys.kt` — REAL

```kotlin
class BoolKey internal constructor(val name: String, val default: Boolean) {
    internal val key: Preferences.Key<Boolean> = booleanPreferencesKey(name)
}
class IntKey internal constructor(val name: String, val default: Int) {
    internal val key: Preferences.Key<Int> = intPreferencesKey(name)
}
class StringKey internal constructor(val name: String, val default: String) {
    internal val key: Preferences.Key<String> = stringPreferencesKey(name)
}
object PrefKeys {
    val darkMode = BoolKey("dark_mode", false)
    val launchCount = IntKey("launch_count", 0)
    val userName = StringKey("user_name", "")
    val allNames: List<String> = listOf(darkMode.name, launchCount.name, userName.name)
}
```

### C2. DataStore factory and scope — `DataStoreFactory.kt` — REAL

```kotlin
fun createDataStore(
    migrations: List<DataMigration<Preferences>>,
    producePath: () -> String,
): DataStore<Preferences>          // PreferenceDataStoreFactory.createWithPath

internal fun defaultStorageScope(): CoroutineScope
// SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
```

### C3. Repository — `PrefsRepository.kt` — STUB, implemented by WP-A

```kotlin
class PrefsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    fun getBoolean(key: BoolKey): Boolean
    fun putBoolean(key: BoolKey, value: Boolean)
    fun getInt(key: IntKey): Int
    fun putInt(key: IntKey, value: Int)
    fun getString(key: StringKey): String
    fun putString(key: StringKey, value: String)
    fun observeBoolean(key: BoolKey): Flow<Boolean>
    fun observeInt(key: IntKey): Flow<Int>
    fun observeString(key: StringKey): Flow<String>
    suspend fun flush()
}
```

Behaviour every consumer may rely on:

- **S1.** The constructor blocks until the stored preferences are loaded. After it returns, getters never suspend or block.
- **S2.** A getter returns the stored value, or `key.default` when nothing is stored.
- **S3.** After `putX(k, v)` returns, `getX(k)` returns `v` immediately on any thread.
- **S4.** Puts are saved to DataStore in the background in the order they were made.
- **S5.** `flush()` returns once every put made before the call is saved.
- **S6.** `observeX(k)` emits the current value on collection, then each new value; it does not emit a value equal to the previous one.
- **S7.** Any thread may call any method.

### C4. Android wiring — `storage/src/androidMain/.../AndroidStorage.kt` — STUB, implemented by WP-B

```kotlin
fun prefsRepository(context: Context): PrefsRepository
internal fun androidDataStore(context: Context, fileName: String = "app.preferences_pb"): DataStore<Preferences>
internal fun resetStorageForTest()
```

- `prefsRepository` returns the same instance for the life of the process and is the only production caller of `androidDataStore`.
- `androidDataStore` stores at `context.filesDir/datastore/<fileName>` with `listOf(SharedPreferencesMigration(context, "app_prefs"))`.
- `resetStorageForTest` clears the cached instance only.

### C5. iOS wiring — `storage/src/iosMain/...` — STUB, implemented by WP-C

```kotlin
// IosStorage.kt
object IosStorage { val repo: PrefsRepository }      // lazy; Swift: IosStorage.shared.repo
internal fun iosStorePath(): String                   // <Application Support>/app.preferences_pb, directory created

// UserDefaultsMigration.kt
class UserDefaultsMigration(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DataMigration<Preferences>

// SwiftBridge.kt
fun interface Cancellable { fun cancel() }
fun PrefsRepository.watchBoolean(key: BoolKey, onChange: (Boolean) -> Unit): Cancellable
fun PrefsRepository.watchInt(key: IntKey, onChange: (Int) -> Unit): Cancellable
fun PrefsRepository.watchString(key: StringKey, onChange: (String) -> Unit): Cancellable
```

- `watchX` calls `onChange` on the main thread, first with the current value, then on each change, until `cancel()`.
- The migration copies `isDarkMode → dark_mode`, `launchCount → launch_count`, `userName → user_name`, sets marker `__ud_migrated = true`, and leaves `NSUserDefaults` untouched.

### C6. Shared UI — `shared-ui/src/.../ui/` — STUB, implemented by WP-D

```kotlin
// commonMain, package com.example.unifiedprefs.ui
@Composable fun SharedSettingsScreen(repo: PrefsRepository, modifier: Modifier = Modifier)

// iosMain, same package
fun MainViewController(): UIViewController     // ComposeUIViewController { SharedSettingsScreen(IosStorage.repo) }
```

Exact on-screen strings: title `Shared (CMP)`; row label `Dark mode`; switch content description `Shared dark mode`; text field label `Shared user name`; text `Shared launch count: <n>`.

### C7. Native prefs classes — public surface frozen

```kotlin
// androidApp, package com.example.unifiedprefs
class LegacyPrefs(context: Context) {
    var darkMode: Boolean; var launchCount: Int; var userName: String
}
```

```swift
// iosApp
final class LegacyPrefs {
    static let shared: LegacyPrefs
    var isDarkMode: Bool
    var launchCount: Int
    var userName: String
}
```

### C8. How Swift sees the Kotlin API (framework `Shared`)

```swift
import Shared
let repo = IosStorage.shared.repo
repo.getBoolean(key: PrefKeys.shared.darkMode)                 // Bool
repo.putBoolean(key: PrefKeys.shared.darkMode, value: true)
repo.getInt(key: PrefKeys.shared.launchCount)                  // Int32
repo.putInt(key: PrefKeys.shared.launchCount, value: Int32(4))
repo.getString(key: PrefKeys.shared.userName)                  // String
let handle = repo.watchBoolean(key: PrefKeys.shared.darkMode) { value in /* value.boolValue */ }
handle.cancel()
MainViewControllerKt.MainViewController()                      // UIViewController
```

Wave 0 confirms these spellings against the generated framework header and corrects them in `docs/CONTRACTS.md`.

### C9. State line and seed hook (how scripts verify the apps)

Both apps print one line after the launch-count increment, once per process start:

```
UNIFIEDPREFS_STATE dark=<true|false> name=<userName> count=<launchCount>
```

- Android: `Log.i("UnifiedPrefs", line)` in `PocApp.onCreate`.
- iOS: `print(line)` in `PocApp.init`.
- iOS seed hook: when the process arguments contain `--seed`, `PocApp.init` sets `LegacyPrefs.shared.isDarkMode = true` and `LegacyPrefs.shared.userName = "Asha"` before the increment. The hook goes through `LegacyPrefs` only.

### C10. Scripts — `scripts/` — REAL, written and proven by Wave 0

| Script | Does | Prints |
|---|---|---|
| `scripts/android-install.sh` | builds and installs `androidApp` debug over any existing install | Gradle result |
| `scripts/android-launch.sh` | force-stops, clears logcat, launches, waits | the `UNIFIEDPREFS_STATE` line |
| `scripts/android-seed.sh` | taps the native switch on and types `Asha` in the native field | nothing |
| `scripts/ios-install.sh` | `xcodegen generate`, builds for simulator `iPhone 16`, boots it, installs over any existing install | `BUILD SUCCEEDED` or the error |
| `scripts/ios-launch.sh [args...]` | terminates, launches with the given args, waits | the `UNIFIEDPREFS_STATE` line |
| `scripts/ios-uninstall.sh`, `scripts/android-uninstall.sh` | remove the app and its data | nothing |

### C11. Test commands — proven by Wave 0

| Scope | Command |
|---|---|
| common tests on JVM | `./gradlew :storage:jvmTest` |
| Android host tests (Robolectric) | `./gradlew :storage:testAndroidHostTest` |
| iOS simulator tests | `./gradlew :storage:iosSimulatorArm64Test` |
| shared UI compiles | `./gradlew :shared-ui:compileAndroidMain :shared-ui:compileKotlinIosSimulatorArm64` |
| Android app builds | `./gradlew :androidApp:assembleDebug` |

---

## Wave 0 — WP-0: contracts, build, stubs, harnesses (main session)

**Owns:** every `build.gradle.kts`, `gradle/libs.versions.toml`, `iosApp/project.yml`, `scripts/*`, `docs/CONTRACTS.md`, all REAL and STUB files above, `storage/src/commonTest/.../FakeDataStore.kt`, and one `SmokeTest.kt` in each of `commonTest`, `androidHostTest`, `iosTest`.

- [ ] **Step 1:** `:storage` build: targets `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, `androidLibrary` with host tests enabled and Android resources included; `api` on datastore-preferences-core and coroutines-core; `androidMain` on datastore-preferences; test deps kotlin-test, coroutines-test, Turbine; Robolectric for Android host tests.
- [ ] **Step 2:** `:shared-ui` build: Compose Multiplatform, `api(project(":storage"))`, iOS framework `baseName = "Shared"`, `isStatic = true`, `export(project(":storage"))`. `androidApp` gains `implementation(project(":shared-ui"))`.
- [ ] **Step 3:** Commit the REAL files (C1, C2) and the STUB files (C3–C6).
- [ ] **Step 4:** `FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences>`, an in-memory implementation over a `MutableStateFlow` guarded by a `Mutex`.
- [ ] **Step 5:** Smoke tests: `commonTest` asserts `PrefKeys.allNames.size == 3`; `androidHostTest` obtains the Robolectric application context and asserts its package name; `iosTest` asserts `iosStorePath().endsWith("/app.preferences_pb")` (so `iosStorePath` is REAL, not a stub).
- [ ] **Step 6:** `brew install xcodegen`. Write `iosApp/project.yml` in its final form: iOS 15 app target `iosApp`, bundle id `com.example.unifiedprefs`, sources `iosApp/`, generated Info.plist, a pre-build script running `./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode` from the repo root, framework search path and `-framework Shared` linker flag, user script sandboxing off. Add a placeholder `iosApp/iosApp/PocApp.swift` that shows the text `placeholder`.
- [ ] **Step 7:** Write the C10 scripts.
- [ ] **Step 8: Gate.** All C11 commands pass, `scripts/ios-install.sh` prints `BUILD SUCCEEDED`, and the generated `Shared.framework` header is checked against C8.
- [ ] **Step 9:** Write `docs/CONTRACTS.md` with any corrections. Commit: `chore: contracts, build config, stubs and test harnesses`. Tag `wave0`.

---

## Wave 1 (base: tag `wave0`)

### WP-A: PrefsRepository

**Owns:** `storage/src/commonMain/.../PrefsRepository.kt`, `storage/src/commonTest/.../PrefsRepositoryTest.kt`, `storage/src/commonTest/.../PrefKeysTest.kt`
**Consumes:** C1, C2, `FakeDataStore`. **Produces:** C3 with S1–S7.

- [ ] **Step 1: Write the tests.** Build the repository as `PrefsRepository(FakeDataStore(seed), defaultStorageScope())` and wrap each test in `runTest`.

```kotlin
// PrefsRepositoryTest
@Test fun returnsDefaultsWhenStoreIsEmpty()
//   getBoolean(darkMode)==false; getInt(launchCount)==0; getString(userName)==""
@Test fun readsSeededValuesWithoutSuspending()
//   seed = preferencesOf(PrefKeys.launchCount.key to 3, PrefKeys.userName.key to "Asha"); getInt==3; getString=="Asha"
@Test fun putIsVisibleToGetImmediately()
//   putInt(launchCount, 5); assertEquals(5, getInt(launchCount)) with no flush
@Test fun putReachesDataStoreAfterFlush()
//   putString(userName, "Asha"); flush(); fake.data.first()[PrefKeys.userName.key] == "Asha"
@Test fun lastOfRapidWritesWins()
//   for (i in 1..100) putInt(launchCount, i); flush(); fake.data.first()[PrefKeys.launchCount.key] == 100
@Test fun observeEmitsCurrentThenChangesAndSkipsDuplicates()
//   observeBoolean(darkMode).test { assertEquals(false, awaitItem()); putBoolean(darkMode, true)
//     assertEquals(true, awaitItem()); putBoolean(darkMode, true); expectNoEvents(); cancel() }

// PrefKeysTest
@Test fun allStoredNamesAreUnique()      // allNames.toSet().size == allNames.size
@Test fun storedNamesMatchTheKeysTable() // allNames == listOf("dark_mode", "launch_count", "user_name")
```

- [ ] **Step 2:** Run `./gradlew :storage:jvmTest`. Expected: the six repository tests fail with `NotImplementedError`; the two key tests pass.
- [ ] **Step 3: Implement** to this design:

```kotlin
private val cache = MutableStateFlow(runBlocking { dataStore.data.first() })
private val pending = MutableStateFlow(0)

private fun write(block: (MutablePreferences) -> Unit) {
    cache.update { it.toMutablePreferences().apply(block).toPreferences() }
    pending.update { it + 1 }
    scope.launch {
        try { dataStore.edit { block(it) } } finally { pending.update { it - 1 } }
    }
}
suspend fun flush() { pending.first { it == 0 } }
```

Getters read `cache.value[key.key] ?: key.default`. Puts call `write { it[key.key] = value }`. Observers are `cache.map { it[key.key] ?: key.default }.distinctUntilChanged()`.

- [ ] **Step 4:** Run `./gradlew :storage:jvmTest`. Expected: 9 tests pass (8 plus the smoke test), 0 fail.
- [ ] **Step 5:** Commit: `feat(storage): PrefsRepository`.

### WP-B: Android storage wiring

**Owns:** `storage/src/androidMain/.../AndroidStorage.kt`, `storage/src/androidHostTest/.../AndroidStorageTest.kt`
**Consumes:** C1, C2, the C3 constructor (a stub is enough). **Produces:** C4.

Tests exercise the DataStore directly, so they pass without WP-A. Each test uses its own `fileName` so no two DataStores share a file.

- [ ] **Step 1: Write the tests** (Robolectric runner, `ApplicationProvider.getApplicationContext()`; `seed(...)` writes to `getSharedPreferences("app_prefs", MODE_PRIVATE)` with `commit()`):

```kotlin
@Test fun migratesExistingSharedPreferences()
//   seed dark_mode=true, launch_count=3, user_name="Asha"
//   val p = androidDataStore(ctx, "t1.preferences_pb").data.first()
//   p[PrefKeys.darkMode.key]==true; p[PrefKeys.launchCount.key]==3; p[PrefKeys.userName.key]=="Asha"
@Test fun migratedKeysAreRemovedFromSharedPreferences()
//   seed the same three; androidDataStore(ctx, "t2.preferences_pb").data.first()
//   getSharedPreferences("app_prefs", MODE_PRIVATE).all.isEmpty()
@Test fun emptySharedPreferencesYieldsNoStoredKeys()
//   androidDataStore(ctx, "t3.preferences_pb").data.first().asMap().isEmpty()
@Test fun storesUnderFilesDirDatastore()
//   androidDataStore(ctx, "t4.preferences_pb").edit { it[PrefKeys.launchCount.key] = 1 }
//   File(ctx.filesDir, "datastore/t4.preferences_pb").exists()
@Test fun accessorReturnsSameInstanceOnEveryCall()
//   resetStorageForTest(); assertSame(prefsRepository(ctx), prefsRepository(ctx))
```

- [ ] **Step 2:** Run `./gradlew :storage:testAndroidHostTest`. Expected: the five tests fail with `NotImplementedError`.
- [ ] **Step 3: Implement.** `prefsRepository` uses a `@Volatile` field with double-checked locking on `context.applicationContext` and builds `PrefsRepository(androidDataStore(appContext), defaultStorageScope())`.
- [ ] **Step 4:** Run `./gradlew :storage:testAndroidHostTest`. Expected: 6 pass, 0 fail. If `accessorReturnsSameInstanceOnEveryCall` fails only because the WP-A stub constructor throws, report that in DEVIATIONS; it is expected to pass after merge.
- [ ] **Step 5:** Commit: `feat(storage): Android singleton and SharedPreferences migration`.

### WP-C: iOS storage, migration and Swift bridge

**Owns:** `storage/src/iosMain/.../IosStorage.kt` (the `IosStorage` object only; leave `iosStorePath` as is), `UserDefaultsMigration.kt`, `SwiftBridge.kt`, `storage/src/iosTest/.../UserDefaultsMigrationTest.kt`
**Consumes:** C1, C2, C3 signatures. **Produces:** C5.

- [ ] **Step 1: Write the tests.** Each creates `NSUserDefaults(suiteName = "test-<name>")`, and removes it afterwards with `removePersistentDomainForName`.

```kotlin
@Test fun mapsIosLegacyKeysToStoredNames()
//   set isDarkMode=true, launchCount=3, userName="Asha"; val out = UserDefaultsMigration(d).migrate(emptyPreferences())
//   out[PrefKeys.darkMode.key]==true; out[PrefKeys.launchCount.key]==3; out[PrefKeys.userName.key]=="Asha"
@Test fun skipsAbsentKeys()
//   set only userName="Asha"; out[PrefKeys.darkMode.key]==null; out[PrefKeys.launchCount.key]==null
@Test fun clampsIntegerOutsideIntRange()
//   d.setInteger(5_000_000_000, forKey = "launchCount"); out[PrefKeys.launchCount.key]==Int.MAX_VALUE
@Test fun setsMarkerAndStopsMigrating()
//   m.shouldMigrate(emptyPreferences())==true; m.shouldMigrate(m.migrate(emptyPreferences()))==false
@Test fun leavesLegacyValuesInPlace()
//   after migrate() and cleanUp(): d.stringForKey("userName")=="Asha"
```

- [ ] **Step 2:** Run `./gradlew :storage:iosSimulatorArm64Test`. Expected: the five tests fail with `NotImplementedError`.
- [ ] **Step 3: Implement the migration** to this design:

```kotlin
private val marker = booleanPreferencesKey("__ud_migrated")
override suspend fun shouldMigrate(currentData: Preferences) = currentData[marker] != true
override suspend fun migrate(currentData: Preferences): Preferences {
    val out = currentData.toMutablePreferences()
    if (defaults.objectForKey("isDarkMode") != null) out[PrefKeys.darkMode.key] = defaults.boolForKey("isDarkMode")
    if (defaults.objectForKey("launchCount") != null) out[PrefKeys.launchCount.key] =
        defaults.integerForKey("launchCount").coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    defaults.stringForKey("userName")?.let { out[PrefKeys.userName.key] = it }
    out[marker] = true
    return out.toPreferences()
}
override suspend fun cleanUp() {}
```

- [ ] **Step 4: Implement `IosStorage` and the bridge.** `repo` is `by lazy { PrefsRepository(createDataStore(listOf(UserDefaultsMigration()), ::iosStorePath), defaultStorageScope()) }`. Each `watchX` launches on `MainScope()` collecting the matching `observeX(key)` into `onChange`, and returns a `Cancellable` that cancels that job.
- [ ] **Step 5:** Run `./gradlew :storage:iosSimulatorArm64Test`. Expected: all tests in `UserDefaultsMigrationTest` and the smoke test pass. Repository tests from WP-A are not in this branch and are not expected.
- [ ] **Step 6:** Commit: `feat(storage): iOS singleton, NSUserDefaults migration, Swift bridge`.

### WP-D: Shared Compose screen

**Owns:** `shared-ui/src/commonMain/.../ui/SharedSettingsScreen.kt`, `shared-ui/src/iosMain/.../ui/MainViewController.kt`
**Consumes:** C1, C3 signatures, `IosStorage.repo`. **Produces:** C6.

- [ ] **Step 1: Implement `SharedSettingsScreen`.** Collect `observeBoolean(darkMode)`, `observeString(userName)` and `observeInt(launchCount)` with `collectAsState`, using the matching getter as the initial value. Wrap the content in `MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme())` and a `Surface`. Lay out a `Column` with 12.dp spacing and 16.dp padding containing, in order: the title, a row with the label and a `Switch` that calls `putBoolean`, an `OutlinedTextField` that calls `putString`, and the count text. Use the exact strings in C6.
- [ ] **Step 2: Implement `MainViewController`** as in C6.
- [ ] **Step 3:** Run `./gradlew :shared-ui:compileAndroidMain :shared-ui:compileKotlinIosSimulatorArm64`. Expected: `BUILD SUCCESSFUL`. There are no unit tests in this package; it is exercised on devices in Waves 2 and 3.
- [ ] **Step 4:** Commit: `feat(shared-ui): shared settings screen`.

### WP-E: iOS before-state app

**Owns:** `iosApp/iosApp/PocApp.swift`, `ContentView.swift`, `LegacyPrefs.swift`
**Consumes:** C7 (Swift), C9, C10 scripts. **Produces:** tag `before-ios`.

Do not `import Shared` in this package. The framework is linked but unused in the before state.

- [ ] **Step 1: Implement `LegacyPrefs`** over `UserDefaults.standard` with keys `isDarkMode`, `launchCount`, `userName`. `launchCount` uses `integer(forKey:)`; `userName` returns `""` when absent.
- [ ] **Step 2: Implement `PocApp`.** In `init`: apply the seed hook (C9), then `LegacyPrefs.shared.launchCount += 1`, then print the state line (C9). The body shows `ContentView()`.
- [ ] **Step 3: Implement `ContentView`.** A `VStack` titled `Native (LegacyPrefs)` with a `Toggle("Dark mode", ...)` (accessibility identifier `nativeDarkMode`), a `TextField("Native user name", ...)`, and `Text("Native launch count: \(count)")`. State is initialised from `LegacyPrefs.shared` and written back on change. Apply `.preferredColorScheme(isDark ? .dark : .light)`.
- [ ] **Step 4: Verify.**

| Command | Expected output |
|---|---|
| `scripts/ios-uninstall.sh; scripts/ios-install.sh` | `BUILD SUCCEEDED` |
| `scripts/ios-launch.sh` | `UNIFIEDPREFS_STATE dark=false name= count=1` |
| `scripts/ios-launch.sh --seed` | `UNIFIEDPREFS_STATE dark=true name=Asha count=2` |
| `scripts/ios-launch.sh` | `UNIFIEDPREFS_STATE dark=true name=Asha count=3` |

- [ ] **Step 5:** Commit: `chore: iOS before-state app`. Tag this commit `before-ios`.

### Wave 1 merge (main session)

Merge into `poc` in this order: A, B, C, D, E. Ownership is disjoint, so conflicts mean a package broke rule 2. Then run every C11 command and `scripts/ios-install.sh`; all must pass, including `accessorReturnsSameInstanceOnEveryCall`. Tag `wave1`.

---

## Wave 2 (base: tag `wave1`)

### WP-F: Android switch-over

**Owns:** `androidApp/src/main/kotlin/com/example/unifiedprefs/LegacyPrefs.kt`, `PocApp.kt`, `MainActivity.kt`
**Consumes:** C3, C4, C6, C7, C9, C10.

- [ ] **Step 1:** Replace the inside of `LegacyPrefs` so each property delegates to `prefsRepository(context)` with the matching `PrefKeys` entry. The public surface in C7 is unchanged, and `SharedPreferences` is no longer referenced in this file.
- [ ] **Step 2:** In `PocApp.onCreate`, after the existing increment, add the state line (C9). Do not change the increment.
- [ ] **Step 3:** In `MainActivity`, read the three values for the native section with `collectAsState` on `prefsRepository(this).observeX(...)`, keep writing through `LegacyPrefs`, and add `SharedSettingsScreen(prefsRepository(this@MainActivity))` below the native section inside the same `Column`.
- [ ] **Step 4: Verify the upgrade path.**

| Command | Expected output |
|---|---|
| `scripts/android-uninstall.sh; git stash -u; git checkout -q before-android; scripts/android-install.sh` | `BUILD SUCCESSFUL` |
| `scripts/android-launch.sh` (the before app prints no state line) then `scripts/android-seed.sh` | no output |
| `scripts/android-launch.sh; scripts/android-launch.sh` | no output; the app has now started 3 times |
| `adb shell run-as com.example.unifiedprefs cat shared_prefs/app_prefs.xml` | `launch_count` 3, `dark_mode` true, `user_name` Asha |
| `git checkout -q wp/F; git stash pop; scripts/android-install.sh` | `BUILD SUCCESSFUL` |
| `scripts/android-launch.sh` | `UNIFIEDPREFS_STATE dark=true name=Asha count=4` |
| `scripts/android-launch.sh` | `UNIFIEDPREFS_STATE dark=true name=Asha count=5` |
| `adb shell run-as com.example.unifiedprefs ls files/datastore` | `app.preferences_pb` |
| `adb shell run-as com.example.unifiedprefs cat shared_prefs/app_prefs.xml` | file missing, or none of the three keys present |

Commit Steps 1–3 before running this table, so the checkout of `before-android` and back loses nothing.

- [ ] **Step 5:** Commit: `feat(android): LegacyPrefs on unified storage; shared screen embedded`.

### WP-G: iOS switch-over

**Owns:** `iosApp/iosApp/LegacyPrefs.swift`, `ContentView.swift`, `ComposeView.swift` (new)
**Consumes:** C5, C6, C7, C8, C9, C10. Do not edit `PocApp.swift`.

- [ ] **Step 1:** Replace the inside of `LegacyPrefs.swift` so each property delegates to `IosStorage.shared.repo` with `PrefKeys.shared` entries, converting `Int32` to and from `Int`. The public surface in C7 is unchanged and `UserDefaults` is no longer referenced.
- [ ] **Step 2:** Add `ComposeView`, a `UIViewControllerRepresentable` whose `makeUIViewController` returns `MainViewControllerKt.MainViewController()`.
- [ ] **Step 3:** In `ContentView`, register `watchBoolean`, `watchString` and `watchInt` in `.onAppear`, storing the three `Cancellable` handles and cancelling them in `.onDisappear`; update the view state from the callbacks. Keep writing through `LegacyPrefs.shared`. Add `ComposeView()` below the native section.
- [ ] **Step 4: Verify the upgrade path.**

| Command | Expected output |
|---|---|
| `scripts/ios-uninstall.sh; git stash -u; git checkout -q before-ios; scripts/ios-install.sh` | `BUILD SUCCEEDED` |
| `scripts/ios-launch.sh --seed` | `UNIFIEDPREFS_STATE dark=true name=Asha count=1` |
| `scripts/ios-launch.sh; scripts/ios-launch.sh` | ends with `... count=3` |
| `git checkout -q wp/G; git stash pop; scripts/ios-install.sh` | `BUILD SUCCEEDED` |
| `scripts/ios-launch.sh` | `UNIFIEDPREFS_STATE dark=true name=Asha count=4` |
| `scripts/ios-launch.sh` | `UNIFIEDPREFS_STATE dark=true name=Asha count=5` |

The fourth row is the central check of the POC: synchronous reads in `PocApp.init`, before any UI, see migrated data. Commit Steps 1–3 before running this table.

- [ ] **Step 5:** Commit: `feat(ios): LegacyPrefs on unified storage; shared screen embedded`.

### Wave 2 merge (main session)

Merge F then G into `poc`. Run every C11 command. Tag `wave2`.

---

## Wave 3 — WP-H: integration, measurements, findings (main session)

- [ ] **Step 1: Cross-section reactivity, Android.** Toggle and type in the native section and confirm the shared section follows; then the reverse. Relaunch and confirm both retain.
- [ ] **Step 2: Cross-section reactivity, iOS.** The same on the simulator.
- [ ] **Step 3: First-launch defaults.** Uninstall, install the current build with no before state, launch. Expected on both platforms: `UNIFIEDPREFS_STATE dark=false name= count=1`.
- [ ] **Step 4: Timings.** Log the elapsed time around the first repository access in each `PocApp`, over five cold starts per platform, and take the median.
- [ ] **Step 5: `FINDINGS.md`.** The answer to the POC question; every verification result from WP-F, WP-G and Steps 1–4; the timings; that Android keeps no rollback copy while iOS does; every `BLOCKED` report and deviation from the packages; what was not covered (encryption, widgets, large data, physical devices).
- [ ] **Step 6:** Final whole-branch review by a fresh reviewer on the most capable model, with the Review Focus list below. Fix Critical and Important findings test-first. Commit: `docs: POC findings`.

## Review Focus

1. **First launch with no legacy data:** defaults everywhere, no crash. (WP-A `returnsDefaultsWhenStoreIsEmpty`, WP-B `emptySharedPreferencesYieldsNoStoredKeys`, WP-H Step 3.)
2. **iOS legacy key absent or out of range:** skipped or clamped, no crash. (WP-C `skipsAbsentKeys`, `clampsIntegerOutsideIntRange`.)
3. **Migration runs once:** Android by deleting legacy keys, iOS by marker. (WP-B `migratedKeysAreRemovedFromSharedPreferences`, WP-C `setsMarkerAndStopsMigrating`.)
4. **Rapid writes to one key:** the last write is what is saved. (WP-A `lastOfRapidWritesWins`.)
5. **Singleton accessor called twice:** same instance, one DataStore. (WP-B `accessorReturnsSameInstanceOnEveryCall`.)
