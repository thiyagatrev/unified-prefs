# UnifiedPrefs POC: Findings

## The question

Can one KMP storage module backed by Jetpack DataStore replace `SharedPreferences` (Android) and
`NSUserDefaults` (iOS) underneath existing native preference classes, without changing their call
sites or losing existing users' data?

## Answer

**Yes, on the emulator and simulator, with three conditions.**

1. Reads must be served from memory. DataStore has no synchronous read, and native code reads
   preferences synchronously at app start.
2. The first access blocks the calling thread while the file loads. With three keys, that was about
   100 ms on the Android emulator. With 200 keys it was about 110–120 ms on Android and 14–52 ms
   on iOS; see "Real-app scale".
3. The old values must be on disk when the new build first runs. On the iOS simulator they
   sometimes were not (see "Problems found").

A fresh reviewer read the whole branch afterwards and found no critical defects and five important
ones. Four are fixed and one is a correction to this report; see "Found by the final review". The
real-app-scale work had its own review, whose findings are folded into that section.

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

This first round measured no baseline for `SharedPreferences` or `NSUserDefaults`. "Real-app scale" below
does, with 200 keys.

### Automated tests

| Host | Tests | Result |
|---|---|---|
| JVM | 31 | pass |
| Android host (Robolectric) | 38 | pass |
| iOS simulator | 41 | pass |

They cover defaults, immediate read-after-write, ordered saves, the Android singleton, both
migrations, clamping of an out-of-range iOS integer, that each migration runs once, a failed save,
(JVM only) an unreadable store file, and (Android only) that a test reset closes the store so the
same file can be opened again. The real-app-scale work added tests for the Long, Float, Double and
string-set types, the save counter, the checksum against a fixed Python test vector, the benchmark
routine, the registry-driven iOS migration (every type, numbers stored as the wrong kind, Unicode),
and Android's migration of Long, Float and set values.

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

- **One store per process is enforced, not just documented.** `PrefsRepository`'s constructor and the DataStore
  factory are `internal`; apps can only reach the store through `prefsRepository(context)` or
  `IosStorage.shared.repo`. DataStore is single-process: Android components in another process, and iOS
  widgets or extensions, would each get their own store.
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

The three demo keys showed that the design works; they could not show whether it holds at a real
app's size. So a generator (`tools/genprefs.py`) wrote **200 preferences** of every type into the
old store of each before-state build:

| Type | Count | Values |
|---|---|---|
| Boolean | 70 | |
| Int | 40 | includes `Int.MIN_VALUE`, `Int.MAX_VALUE`, 0 |
| Long | 24 | millisecond timestamps (2015–2030), plus `Long.MIN_VALUE`, `Long.MAX_VALUE`, 0 |
| Float | 16 | 32-bit values |
| Double | 8 | iOS only; `SharedPreferences` has no Double type |
| String, short | 30 | includes empty, emoji, Tamil, `<`, `&`, quotes, newline, tab, `]]>` |
| String, medium | 6 | 1–4 KB tokens and small JSON |
| String, large | 2 | typical: 16–38 KB JSON; heavy: 1.0–1.45 MB JSON |
| String set | 4 | 3–50 entries |

There are two profiles over the same 200 keys: **typical**, about 77 KB of values, and **heavy**,
about 2.4 MB, the case where an app caches API responses in preferences. The generator checks its
own output (15 checks), including that every kind stays inside the ranges above.

Each run (`scripts/bench.sh`):
1. installs the before-state build and seeds the profile;
2. **launches the old build once**, so Android and iOS rewrite the store in their own format, as a
   real user's file would be;
3. installs the new build and launches once, which migrates;
4. after the host has gone quiet, makes 11 cold starts measuring DataStore and 11 measuring the old
   store reading the same data, alternating.

### Every value arrived exactly

After migration, a checksum over all 200 values matched the generator's expected value on both
platforms, in both profiles, on all 48 DataStore launches. The extreme integers, the 32-bit floats,
the emoji and Tamil text, the XML special characters and the megabyte-sized strings all arrived
unchanged.

One limit of the check: a value equal to its default (about 35 of the 200, mostly `false`, `0` and
`""`) would give the same checksum if the migration had dropped it. The other values, and the
upgrade tests of the demo keys, show that the migration copies; they do not prove each one of those
35 by itself.

### Numbers

Median and (range) in milliseconds, Debug builds, Android emulator (Pixel 5, API 35) and iOS
simulator (iPhone 17, iOS 26.5), 11 cold starts per mode. Raw data is in `results/`.

| Platform, profile | Runs | First access, DataStore ms | Migration run ms | Key setup ms | First read, old store ms | One write, DataStore ms | One write, old store ms | 100 quick writes ms | Saves for 100 writes | DataStore file | Old store file | Old read ÷ DataStore access, per run | Host load |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| android, typical | 11 | 121 (83–195) | 163 | 13 (11–17) | 112 (104–305) | 55 (51–57) | 15 (11–52) | 50 (42–74) | 2 (2–3) | 82 KB | 142 KB | 1.31 (0.53–3.01) | 10.3–15.1 |
| android, heavy | 11 | 110 (85–346) | 776 | 12 (9.7–20) | 677 (645–1045) | 123 (118–141) | 61 (58–76) | 176 (169–207) | 2 (2–2) | 2.4 MB | 4.3 MB | 5.95 (3.02–8.17) | 10.9–13.9 |
| ios, typical | 11 | 14 (10–35) | 20 | 3.4 (2.6–3.9) | 4.3 (2.8–7.5) | 11 (8.0–19) | 0.4 (0.2–0.5) | 110 (68–123) | 9 (8–9) | 82 KB | 149 KB | 0.30 (0.10–0.61) | 7.3–11.2 |
| ios, heavy | 11 | 52 (46–59) | 65 | 2.4 (2.0–3.5) | 5.7 (4.6–11) | 73 (63–222) | 0.6 (0.3–10) | 206 (136–359) | 3 (2–3) | 2.4 MB | 4.8 MB | 0.11 (0.08–0.19) | 10.8–45.6 |

How to read the columns:

- **First access.** The first storage call at app start: create the store, read the file, build the
  in-memory copy. It blocks whatever thread makes it; the sample makes it on the main thread.
- **Migration run.** The same, on the one launch that also copies the old store.
- **Key setup.** Creating the 205 key objects, timed just before first access. iOS's migration walks
  every key and Android's does not, so it is timed separately. It is a cost the old store does not
  pay, so add it to the DataStore side when comparing. On iOS it also includes Kotlin runtime start
  and bridging the key list to Swift.
- **First read, old store.** Android: `getSharedPreferences(...).getAll()`. iOS:
  `UserDefaults(suiteName: "baseline").dictionaryRepresentation()`.
- **One write.** DataStore: from `put` until the save has completed. On Android that includes a
  forced flush to disk; on iOS it was not checked whether DataStore forces the flush. Android old
  store: `commit()`, which also waits for the disk. **iOS old store: the `set` call plus
  `synchronize()` only.** `UserDefaults` hands the write to a separate system process, so that
  column is not time to disk and cannot be compared with DataStore's.
- **100 quick writes.** 100 `put` calls in a row, like typing, until the last is saved, and how many
  save attempts that took.
- **Old read ÷ DataStore access, per run.** Each run measures both stores back to back, so their
  ratio is not distorted if the host slows down part-way. Above 1 means DataStore was faster.
- **Host load.** The 1-minute load average when each launch was recorded. Every run waited for it
  to drop below 12 before measuring.

### What the numbers say

1. **Android, typical size: start-up is about the same as today.** DataStore 121 ms against
   112 ms, with ranges that overlap and a per-run ratio either side of 1. Key setup adds about
   13 ms on top.
2. **Android, heavy: DataStore starts about 6× faster.** 110 ms against 677 ms; DataStore was faster
   in every one of the 11 runs. Parsing 4.3 MB of XML is slower than reading 2.4 MB of protobuf.
3. **iOS: DataStore starts slower than `UserDefaults`.** 14 ms against 4.3 ms typical, and 52 ms
   against 5.7 ms heavy, plus 2–4 ms of key setup; `UserDefaults` was faster in every run. Two
   likely causes, neither tested here: `UserDefaults` is served by a system process (`cfprefsd`)
   that keeps preferences in memory between launches, and the Kotlin/Native code ran as an
   unoptimised Debug build.
4. **Both stores rewrite the whole file on every write**: DataStore 2.4 MB, `SharedPreferences`
   4.3 MB in the heavy profile, and `cfprefsd` rewrites the whole plist. Even so, a DataStore write
   took about 2–4× as long as `commit()`: 55 against 15 ms typical, 123 against 61 ms heavy. Part of
   that is a Debug build running DataStore as unoptimised app code, while `SharedPreferences` is
   compiled into the system. The app never waits for the write, because the value is in memory at
   once. A crash before the save finishes loses the change, as with `SharedPreferences.apply()`.
5. **Rapid writes are batched.** 100 writes needed 2–9 save attempts and finished in 50–206 ms
   (medians).
6. **The DataStore file is roughly half the size of the old store.** The Android XML writes every
   `"` inside a value as `&quot;`, which doubles JSON values; plain text barely grows. A binary plist
   stores any string containing a non-ASCII character as UTF-16, which doubles it.
7. **The heavy iOS old store is past a real limit, and iOS says so.** Its plist is 4.8 MB. Every
   write to it made iOS log, at fault level:
   *"Attempting to store >= 4194304 bytes of data in CFPreferences/NSUserDefaults on this platform
   is invalid. This is a bug in iosApp or a library it uses."*
   On the simulator the writes were still kept: the benchmark counter went up on every run. It
   crosses 4 MB only because a few Tamil characters make each 1.2 MB JSON string UTF-16. The same
   cache in plain ASCII would be about 2.5 MB. DataStore stored the same values in 2.4 MB with no
   warning.
8. **The migration costs one slower launch, once.** On Android 163 ms typical and 776 ms heavy,
   against about 120 ms for a normal launch. On iOS 20 ms and 65 ms.

### What this means for an app

- **With preferences of a normal size, the cost is small.** On Android, start-up is about the same
  plus ~13 ms of key setup; on iOS it is about 13 ms slower. In exchange the platform split goes
  away, every platform gets synchronous typed reads from memory, and every value migrates exactly.
  These are Debug-build numbers, which penalise only the DataStore side.
- **Do not keep megabytes in preferences, with any store.** At 2.4 MB every write rewrites
  megabytes, the Android migration launch takes most of a second, and the old iOS store is past the
  size Apple calls invalid. Cached responses belong in files or a database, before and after this
  migration.
- **To take first access off the critical path, start it early on a background thread**, for
  example by calling `prefsRepository(context)` from a thread started in `Application.onCreate`, or
  touching `IosStorage.shared.repo` from a background queue at app start. This helps only if the
  first synchronous read comes later than that. The sample reads in `onCreate` and in the iOS `App`
  initialiser straight away, so it would still wait.
- **On the keys:** 200 keys sit in one `object` here, and that is fine. The JVM limit people quote
  is 255 *parameters*, which rules out a data class holding hundreds of keys, not an `object`. An
  `object` with 1000 keys compiled on every target; its real ceiling, the 64 KB limit on a class's
  initialiser, is several times further away. Past a few hundred keys, split them into one `object`
  per feature, still inside the storage module so their constructors stay `internal`, plus one
  combined list for the migration. The main reason is ownership and merge conflicts. Callers do not
  change: native code keeps its getter/setter wrapper, and shared code uses the keys directly.
- **The benchmark code is not part of the design.** `PrefsBench`, the two bench keys and the
  `bench_mode` check live in production code here only to measure the sample.

### How much to trust this

- **These are emulator and simulator numbers, not phone numbers.** Read them as DataStore relative
  to the old store on the same machine.
- **Debug builds penalise only DataStore.** `SharedPreferences` and `UserDefaults` are system code
  and run optimised either way. The comparisons are therefore worst-case for DataStore. A release
  build (Android minified and non-debuggable, iOS Release configuration) should be measured before
  quoting absolute differences.
- **The host was a busy laptop** (a browser and other apps running). Runs were made with only one
  device image running and only after the load average dropped below 12. Earlier attempts recorded
  during heavy load, including the first version of this section, were discarded. In the iOS heavy
  run the load rose to 46 for some launches; its DataStore times stayed within 46–59 ms.
- **Both stores read from a warm OS file cache** after the first launch, which is also the common
  real case.
- **"Save attempts" counts passes of the background saver**; a pass that finds nothing new to write
  is still counted.
- **Not measured:** physical devices, release builds, memory use, an update from a real app store,
  and a process killed during a save.

## How the work was run

**Real-app scale.**
- **Wave 0, the coordinating session:** stubs and the two risky facts, that a 1000-key object
  compiles everywhere and that the iOS old store can be seeded from outside the app.
- **Wave 1, four packages in parallel:** generator, repository types, checksum, iOS migration.
- **Wave 2, two packages:** the benchmark routine with the Android mode, and the iOS mode. The iOS
  package reported BLOCKED because a stub had an outdated signature; that was a coordinator error,
  fixed at merge.
- **Wave 3, the coordinating session:** the benchmark script, the runs, this report, and a review.
  The review found the Android fixture was not in Android's own format and that three data kinds
  were outside their stated ranges; both were fixed and everything was re-run.

**First POC.**

Wave 0 (build setup, contracts as compiling stubs, test harnesses, scripts) was done in one session.
Seven work packages then ran as lightweight agents in separate git worktrees, five in parallel and
then two, each owning its own files. All seven reported done on the first attempt and merged without
conflicts. Both defects under "Problems found" items 1 and 2 were found afterwards by the
coordinating session on the merged code, and the design-level ones by a separate reviewer; none
were caught by the packages' own checks.
