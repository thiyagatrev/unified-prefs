# UnifiedPrefs POC: Findings

## The question

Can one KMP storage module backed by Jetpack DataStore replace `SharedPreferences` (Android) and
`NSUserDefaults` (iOS) underneath existing native preference classes, without changing their call
sites or losing existing users' data?

## Answer

**Yes, on the emulator and simulator, with three conditions.**

1. Reads must be served from memory. DataStore has no synchronous read, and native code reads
   preferences synchronously at app start.
2. The first access blocks the calling thread while the file loads. On the Android emulator that
   was about 100 ms on the main thread.
3. The old values must be on disk when the new build first runs. On the iOS simulator they
   sometimes were not (see "Problems found").

A fresh reviewer read the whole branch afterwards and found no critical defects and five important
ones. Four are fixed and one is a correction to this report; see "Found by the final review".

## What was built

| Part | What it is |
|---|---|
| `:storage` | One DataStore, a typed key registry (`PrefKeys`), and `PrefsRepository`: synchronous reads from an in-memory copy, background saves in write order, Flows for observers |
| `:shared-ui` | A Compose Multiplatform settings screen; also the single iOS framework, re-exporting `:storage` |
| `androidApp`, `iosApp` | Native apps whose `LegacyPrefs` class kept its public surface while its internals moved from the platform store to the repository |

Versions: Kotlin 2.4.10, AGP 9.1.1, Compose Multiplatform 1.11.1, DataStore 1.3.0, coroutines 1.11.0,
Gradle 9.6.1, Xcode 26.6.

## Results

### Upgrade over an existing install

The before-state build was installed, set to dark mode on, name `Asha`, and launched three times.
The new build was then installed over it. The launch count is incremented synchronously at process
start, before any UI.

| | Before | First launch after | Second launch after | Old store afterwards |
|---|---|---|---|---|
| Android (Pixel 5 emulator, API 35) | dark on, `Asha`, 3 | dark on, `Asha`, 4 | dark on, `Asha`, 5 | `app_prefs.xml` deleted |
| iOS (iPhone 17 simulator, iOS 26.5) | dark on, `Asha`, 3 | dark on, `Asha`, 4 | dark on, `Asha`, 5 | values still in `NSUserDefaults` |

Run on the merged code by the coordinating session: once on Android, twice on iOS.

### Fresh install with no old data

Both platforms reported dark off, empty name, count 1 on first launch.

### Native and shared sections stay in sync

| | Native change shows in shared section | Shared change shows in native section | Survives relaunch |
|---|---|---|---|
| Android | yes (switch and text) | yes (switch and text) | yes |
| iOS | yes (switch and text) | yes (switch and text) | yes |

Checked by hand on the emulator and simulator: toggling the switch and typing in one section, then
reading the other. On iOS this path is separate code from Android (callback watchers into SwiftUI
state), and it has no automated test.

### Time blocked on first access

Debug builds on an emulator and a simulator, so indicative only. This is the time for the first
call that creates the DataStore, runs any migration and loads the file.

| | Cold start, median of 5 | Range | Launch that ran the migration | First launch, empty store |
|---|---|---|---|---|
| Android | 100.6 ms | 94.0–118.0 ms | 115.7 ms | 80.0 ms |
| iOS | 9.5 ms | 7.1–11.0 ms | 38.7 ms | 30.9 ms |

No baseline was measured for `SharedPreferences` or `NSUserDefaults`, so these are not a comparison.

### Automated tests

| Host | Tests | Result |
|---|---|---|
| JVM | 31 | pass |
| Android host (Robolectric) | 38 | pass |
| iOS simulator | 41 | pass |

They cover defaults, immediate read-after-write, ordered saves, the Android singleton, both
migrations, clamping of an out-of-range iOS integer, that each migration runs once, a failed save,
(JVM only) an unreadable store file, and (Android only) that a test reset closes the store so the
same file can be opened again.

## Problems found

1. **iOS app crashed when the Compose screen appeared.** Compose Multiplatform refuses to start on
   iPhone unless `Info.plist` contains `CADisableMinimumFrameDurationOnPhone = true`. The first
   round of checks missed it because they only read a line printed before the UI. Fixed in
   `iosApp/project.yml`; `scripts/ios-launch.sh` now fails if the app crashes within three seconds.
2. **iOS simulator lost unwritten `NSUserDefaults` on reinstall.** In three of three trials, only the
   first key written was on disk when the new build was installed, and the install moved the app's
   data container. The migration then found one key instead of three. `scripts/ios-install.sh` now
   restarts the simulator before installing, which forces the write. Whether a real device can lose
   values the same way across an App Store update was not tested. If it can, the loss is permanent:
   the migration sets its marker on the first run, so keys that were missing at that moment are
   never copied later, even though they remain in `NSUserDefaults`.
3. **Android keeps no rollback copy.** `SharedPreferencesMigration` deletes the keys it migrates, and
   that deletion is what stops it running twice. A build rolled back to `SharedPreferences` would
   start from empty. The iOS migration leaves the old values and uses a marker key instead.
4. **Two imports in the plan were wrong for DataStore 1.3.0.** `toMutablePreferences` and
   `toPreferences` are member functions, not top-level ones. Two packages hit and fixed this.
5. **AGP 9 changed the KMP Android setup.** Library modules need the
   `com.android.kotlin.multiplatform.library` plugin, the `androidLibrary { }` block, and
   `withHostTest { }` for Robolectric tests in a source set named `androidHostTest`.

## Found by the final review

| Finding | What a user would see | Status |
|---|---|---|
| Two threads writing the same key could save in a different order than they updated memory | A setting that reverts after relaunch | Fixed by design: the saver now writes the latest whole snapshot. A concurrency test was added, but it also passed on the old code, so the race was never reproduced |
| A failed background save threw inside a coroutine with no handler | A crash some time after changing a setting | Fixed: failures are counted in `saveFailures`, memory keeps the value, and the next save retries it |
| An unreadable store file threw on first read | A crash on every launch | Fixed: the file is replaced with empty preferences, which loses the stored values |
| The iOS native section wrote back every value its watcher delivered | A just-made edit in the shared section could be overwritten | Fixed: only user input writes. The race itself was never observed |
| This report overstated the iOS sync evidence | — | Corrected above |

The rewritten save path was re-run through the upgrade, fresh-install and sync checks on both
platforms after the fix.

The five smaller points the review raised were fixed afterwards:

| Point | Fix | How it was checked |
|---|---|---|
| Swift `LegacyPrefs` trapped on a launch count above the 32-bit range | Clamps to the 32-bit range instead | Build only |
| Compose screens created a new Flow on each recomposition | Flows are remembered per repository | Build, and the sync checks below |
| iOS watchers were not cancelled if `onAppear` ran twice | Earlier watchers are cancelled first | Build only; `onAppear` never ran twice in the checks |
| `resetStorageForTest()` left the old DataStore open | The store and saver share a parent job that the reset cancels | New Android host test, failing first with "multiple DataStores active for the same file" |
| A text field could briefly show an older name | A watched value is shown only if memory still holds it | Fast typing in each field on both platforms; the glitch itself was never observed |

After these fixes the upgrade (3 → 4 → 5), two-way sync with fast typing, and relaunch checks
were run again on the Android emulator and the iOS simulator, and passed.

## Design points that held up

- **Key constructors are `internal`.** Native code cannot declare a key or pass a string; it can only
  use entries of `PrefKeys`. Defaults live on the key, so platforms cannot disagree.
- **Swift sees a usable API.** `IosStorage.shared.repo.getBoolean(key: PrefKeys.shared.darkMode)` works
  directly. Flows are bridged with small callback functions (`watchBoolean` and so on) that return a
  cancel handle; no extra library was needed. This bridge is verified by hand only.
- **One iOS framework.** `:shared-ui` exports `:storage`, so Swift imports a single `Shared` module.

## Not covered

- Encryption of stored values.
- iOS widgets, extensions and app groups.
- Physical devices, release builds, and real App Store or Play Store updates.
- Archived iOS objects (`NSData`, `NSDate`, dictionaries) in `UserDefaults`.
- A process killed between a write and its background save. The design accepts this, as
  `SharedPreferences.apply()` does, but no test measures it.
- A save that keeps failing. Memory and disk then stay apart until a save succeeds, and the app
  is told only through a counter.
- Writes from several threads under real load. The design no longer depends on queue order, but
  no test has shown the old failure.

## Real-app scale: 200 preferences

### What was run

The three demo keys showed the design works; they could not show whether it holds at a real
app's size. So a generator (`tools/genprefs.py`) wrote **200 preferences** of every type into the
old stores of the before-state builds, which were then upgraded:

| Type | Count | Values |
|---|---|---|
| Boolean | 70 | |
| Int | 40 | includes `Int.MIN_VALUE`, `Int.MAX_VALUE`, 0 |
| Long | 24 | timestamps, includes `Long.MIN_VALUE`, `Long.MAX_VALUE` |
| Float | 16 | 32-bit values |
| Double | 8 | iOS only; `SharedPreferences` has no Double type |
| String, short | 30 | includes empty, emoji, Tamil, `<`, `&`, quotes, newline, `]]>` |
| String, medium | 6 | 1–4 KB tokens and small JSON |
| String, large | 2 | typical: 10–40 KB JSON; heavy: 1.0–1.45 MB JSON |
| String set | 4 | 3–50 entries |

There are two profiles over the same 200 keys: **typical**, about 64 KB of values, and **heavy**,
about 2.4 MB, the case where an app caches API responses in preferences.

Each run (`scripts/bench.sh`) installed the before-state build, seeded the profile, upgraded, and
launched once to migrate. It then made 10 cold starts measuring DataStore and 10 measuring the
old store reading the same data, alternating.

### Every value arrived exactly

After migration, a checksum over all 200 values matched the generator's expected value on both
platforms, in both profiles, on every one of the 44 launches. Nothing was lost, rounded or
re-encoded: not the extreme integers, the 32-bit floats, the emoji and Tamil text, the XML
special characters, nor the megabyte-sized strings.

### Numbers

Median and (range) in milliseconds, Debug builds, Android emulator (Pixel 5, API 35) and iOS
simulator (iPhone 17, iOS 26.5).

| Platform, profile | Runs | First access, DataStore ms | Migration run ms | Key setup ms | First read, old store ms | One write, DataStore ms | One write, old store ms | 100 quick writes ms | Saves for 100 writes | DataStore file | Old store file |
|---|---|---|---|---|---|---|---|---|---|---|---|
| android, typical | 10 | 114 (89–201) | 163 | 14 (9.3–38) | 119 (100–446) | 55 (50–76) | 17 (14–34) | 68 (59–91) | 3.0 (3.0–3.0) | 69 KB | 125 KB |
| android, heavy | 10 | 317 (100–776) | 324 | 22 (12–40) | 1794 (147–3531) | 386 (129–822) | 172 (72–367) | 629 (184–1226) | 2.0 (2.0–2.0) | 2.4 MB | 4.3 MB |
| ios, typical | 10 | 13 (8.9–50) | 123 | 3.0 (2.4–4.6) | 4.1 (3.7–10) | 8.9 (7.4–14) | 0.4 (0.3–1.3) | 81 (64–131) | 6.0 (4.0–10) | 69 KB | 131 KB |
| ios, heavy | 10 | 51 (45–72) | 171 | 2.7 (2.2–4.1) | 6.5 (4.8–15) | 73 (64–115) | 0.5 (0.3–4.2) | 174 (141–250) | 2.0 (2.0–3.0) | 2.4 MB | 4.7 MB |

How to read the columns:

- **First access.** The time for the first storage call at app start: create the store, read the
  file, build the in-memory copy. This blocks whatever thread makes the call; the app makes it on
  the main thread.
- **Migration run.** The same, on the one launch that also copies the old store.
- **Key setup.** Creating the 205 key objects, timed separately. iOS's migration walks every key
  and Android's does not, so leaving it inside first access would skew the comparison.
- **First read, old store.** Android: `getSharedPreferences(...).getAll()`. iOS:
  `UserDefaults(suiteName:).dictionaryRepresentation()`.
- **One write.** DataStore: from `put` until the value is on disk. Android old store:
  `commit()`, which also waits for the disk. **iOS old store: the `set` call plus
  `synchronize()` only.** `UserDefaults` hands the write to a separate system process, so this
  number is not time to disk and cannot be compared with the DataStore column.
- **100 quick writes.** 100 `put` calls in a row, like typing, until all are saved, and how many
  saves to disk that took.

### What the numbers say

1. **Typical profile, Android: start-up is the same as today.** DataStore's first access (114 ms)
   is within noise of `SharedPreferences` (119 ms).
2. **Heavy profile, Android: DataStore starts much faster.** 317 ms against 1,794 ms. Parsing
   4.3 MB of XML is slower than reading 2.4 MB of protobuf. The ranges are wide (see "How much to
   trust this").
3. **iOS: DataStore starts slower than `UserDefaults`.** 13 ms against 4 ms typical, and 51 ms
   against 6.5 ms heavy. `UserDefaults` is served by a system process (`cfprefsd`) that keeps
   preferences in memory between launches. DataStore reads and parses its file in the app every
   time. On a real device that daemon cache may be evicted more often than on a simulator.
4. **Every DataStore write rewrites the whole file.** One write costs 55 ms (typical) and 386 ms
   (heavy) on Android, against 17 ms and 172 ms for `commit()`. With 2.4 MB stored, changing one
   flag writes 2.4 MB. The value is in memory at once, so the app never waits. But a crash during
   that window loses the change, as with `SharedPreferences.apply()`.
5. **Rapid writes are batched.** 100 writes became 2–6 saves on both platforms, and finished in
   68–629 ms.
6. **The DataStore file is about half the size of the old store.** The Android XML stores non-ASCII
   text as escaped character codes. A binary plist stores any string containing non-ASCII text as
   UTF-16.
7. **The heavy iOS old store is past a real limit, and iOS says so.** Its plist is 4.7 MB. Every
   write to it made iOS log, at fault level:
   *"Attempting to store >= 4194304 bytes of data in CFPreferences/NSUserDefaults on this platform
   is invalid. This is a bug in iosApp or a library it uses."*
   On the simulator the writes were still kept: the benchmark counter reached 10 after 10 runs.
   Apple calls it invalid, so an app at this size depends on behaviour Apple does not promise.
   DataStore stored the same values in 2.4 MB with no warning; its limits are memory and the time
   to rewrite the file.
8. **The migration costs one slower launch.** 163 ms and 324 ms on Android, 123 ms and 171 ms on
   iOS, for typical and heavy, once, on the first launch after the update.

### What this means for an app

- **With preferences of a normal size, this design costs nothing on Android and about 10 ms at
  iOS start-up.** In exchange it removes the platform split, gives synchronous typed reads from
  memory everywhere, and migrates every value exactly.
- **Do not keep megabytes in preferences, with any store.** At 2.4 MB, every write costs hundreds
  of milliseconds of background disk work, and the old iOS store is past `UserDefaults`' size
  warning. Cached responses belong in files or a database, and that is true before and after
  this migration.
- **If start-up time matters, touch storage early on a background thread** (for example in
  `Application.onCreate` or the iOS app delegate). The first access then overlaps with UI setup
  instead of blocking the main thread.
- **On the keys:** 200 keys sit in one `object` here, and that is fine. The JVM limit people quote
  is 255 *parameters*, which rules out a data class holding hundreds of keys, not an `object` (one
  with 1000 keys compiled on every target). Past a few hundred keys, split them into one `object`
  per feature, still inside the storage module so their constructors stay `internal`, plus one
  combined list for the migration. The reason is ownership and merge conflicts, not the compiler.
  Callers do not change: native code keeps its getter/setter wrapper, and shared code uses the
  keys directly.

### How much to trust this

- **These are emulator and simulator numbers, not phone numbers.** Read them as DataStore relative
  to the old store on the same machine, not as absolute promises.
- **The host was under memory pressure** (a browser and both device images running), so the
  first Android run was discarded. The runs above were made with only one device image running at
  a time. Android ranges are still wide, the heavy profile most of all, so the medians matter more
  than single runs.
- **Both stores read from a warm OS file cache** after the first launch, which is also the common
  real case.
- **Debug builds only.** Release builds would be faster for both stores.
- **Not measured:** physical devices, release builds, memory use, a store update in the wild, and
  a process killed during a save.

## How the work was run

Wave 0 (build setup, contracts as compiling stubs, test harnesses, scripts) was done in one session.
Seven work packages then ran as lightweight agents in separate git worktrees, five in parallel and
then two, each owning its own files. All seven reported done on the first attempt and merged without
conflicts. Both defects under "Problems found" items 1 and 2 were found afterwards by the
coordinating session on the merged code, and the design-level ones by a separate reviewer; none
were caught by the packages' own checks.
