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
| JVM | 13 | pass |
| Android host (Robolectric) | 18 | pass |
| iOS simulator | 18 | pass |

They cover defaults, immediate read-after-write, ordered saves, the Android singleton, both
migrations, clamping of an out-of-range iOS integer, that each migration runs once, a failed save,
and (JVM only) an unreadable store file.

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

Smaller points the review raised and that are **not** fixed: the Swift `LegacyPrefs` traps on a
launch count above the 32-bit range; the Compose screens create a new Flow on each recomposition;
iOS watchers are not cancelled if `onAppear` runs twice; `resetStorageForTest()` leaves the old
DataStore open; and a text field can briefly show an older value before correcting itself.

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
- Large or frequently written data (DataStore rewrites the whole file on each save).
- Physical devices, release builds, and real App Store or Play Store updates.
- Values other than Boolean, Int and String, such as Android string sets or archived iOS objects.
- A process killed between a write and its background save. The design accepts this, as
  `SharedPreferences.apply()` does, but no test measures it.
- A save that keeps failing. Memory and disk then stay apart until a save succeeds, and the app
  is told only through a counter.
- Writes from several threads under real load. The design no longer depends on queue order, but
  no test has shown the old failure.

## How the work was run

Wave 0 (build setup, contracts as compiling stubs, test harnesses, scripts) was done in one session.
Seven work packages then ran as lightweight agents in separate git worktrees, five in parallel and
then two, each owning its own files. All seven reported done on the first attempt and merged without
conflicts. Both defects under "Problems found" items 1 and 2 were found afterwards by the
coordinating session on the merged code, and the design-level ones by a separate reviewer; none
were caught by the packages' own checks.
