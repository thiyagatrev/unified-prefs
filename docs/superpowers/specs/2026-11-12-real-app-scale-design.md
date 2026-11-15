# Real-App Scale: Design

**Status:** draft for review · **Date:** 2026-11-12 · **Branch:** `poc`
**Order:** this work comes before the Medium post. Its results go into `FINDINGS.md`, and the post
draws on them.

## Purpose

The POC proved the design with three preferences. Readers deciding whether to use it in their own
app need to know it holds at the size and variety of a real app, and how it compares with what
their app does today. This work answers four questions, on both platforms, for a typical app and
a heavy one:

1. Does app start get slower than with `SharedPreferences` / `NSUserDefaults` on the same data?
2. Does the one-time migration copy every value exactly?
3. How long can a single change sit in memory before it is on disk (the crash-loss window)?
4. Does fast repeated input (typing) pile up disk work?

## Why 200 keys, in one object

200 is a believable count for a mid-size app, and one object keeps the sample plain. The sample
deliberately does not build per-feature key objects. Instead the post gives that as advice:

- **The real JVM limit is on parameters, not fields.** A method or constructor takes at most 255
  parameters, so a *data class* cannot hold hundreds of keys. An `object` can: Wave 0 compiled 1000
  keys in one object on every target.
- **The reason to split is people, not the compiler.** Past a few hundred keys, one file means merge
  conflicts, no clear ownership and dead keys nobody dares delete. Then split keys into one
  `object` per feature, still inside the storage module so constructors stay `internal`, with one
  combined list for the migration.
- **Callers do not change when keys are split.** Native code uses a getter/setter wrapper over the
  repository (as `LegacyPrefs` does), and code that wants direct access uses the key objects. Both
  read the same in-memory copy.

## Scope

In scope:
- 200 generated preferences covering Boolean, Int, Long, Float, Double, String and string set.
- Two size profiles over the same 200 keys: **typical** (about 70 KB) and **heavy** (about 2.5 MB).
- New value types in the repository, a registry-driven iOS migration, a benchmark launch mode, and
  scripts that seed the old stores and collect results.
- Baseline measurements of the old stores on the same data.

Out of scope: encryption, physical devices (added later only if one is available), release
builds, app groups and extensions, byte-array values, memory profiling.

## The data

### Keys

One Python script (`tools/genprefs.py`) with a fixed seed generates everything. Running it twice
gives byte-identical output.

| Type | Count | Values |
|---|---|---|
| Boolean | 70 | true / false |
| Int | 40 | includes 0, negatives, `Int.MIN_VALUE`, `Int.MAX_VALUE` |
| Long | 24 | timestamps in ms, includes `Long.MIN_VALUE`, `Long.MAX_VALUE` |
| Float | 16 | values exactly representable as 32-bit floats; no NaN or infinity |
| Double | 8 | iOS only (see below) |
| String, short | 30 | 0–64 chars; includes the empty string |
| String, medium | 6 | 1–4 KB: token-like strings and small JSON |
| String, large | 2 | typical: 10–40 KB JSON; heavy: 1.0–1.5 MB JSON |
| String set | 4 | 3–50 short strings each |
| **Total** | **200** | |

Strings include non-ASCII text (emoji, non-Latin scripts) and characters XML and plists must escape
(`<`, `&`, quotes, newlines), because real names and cached JSON contain them.

Names look like real keys (`feature_flag_0042`, `last_sync_ts_017`). The two profiles use the same
200 keys; only the 2 large strings change size.

**Double on Android.** `SharedPreferences` has no Double type, so the Android old store holds no
Double keys; those 8 keys read their defaults on Android after migration. iOS holds all 200. The
expected checksum is therefore computed per platform.

### Generated outputs

| Output | Used by |
|---|---|
| `storage/src/commonMain/.../GeneratedKeys.kt`: one `object GeneratedKeys` with 200 typed keys and `val all: List<PrefKey>` | Repository, migration, benchmark |
| `build/genprefs/<profile>/app_prefs.xml` | Seeding the Android old store |
| `build/genprefs/<profile>/legacy.plist` | Seeding the iOS old store |
| `build/genprefs/<profile>/expected-<platform>.txt` | The checksum the app must report |

`GeneratedKeys.kt` is committed, so a build never needs Python. The script also checks itself:
it parses the XML and plist it wrote and confirms they decode to the values it generated.

## Code changes

### Keys (`PrefKeys.kt`)

- A sealed base `PrefKey` with `name`, plus `legacyIosName` (defaults to `name`). The existing three
  keys set it to `isDarkMode`, `launchCount` and `userName`.
- New key classes `LongKey`, `FloatKey`, `DoubleKey` and `StringSetKey`, with `internal`
  constructors like the existing three.
- `PrefKeys` keeps its three demo keys. The generated keys live in one `object GeneratedKeys`, and
  `allKeys` lists both.

### Repository (`PrefsRepository.kt`)

- `get`/`put`/`observe` for Long, Float, Double and `Set<String>`, built the same way as the
  existing three types.
- `saveCount: StateFlow<Int>`, the number of saves completed, so the benchmark can count disk
  writes. It sits next to the existing `saveFailures`.
- Swift watchers `watchLong`, `watchFloat`, `watchDouble` and `watchStringSet` in `SwiftBridge.kt`.

### iOS migration (`UserDefaultsMigration.kt`)

It currently copies three keys by hand. It will walk `allKeys` instead and read each
`legacyIosName` according to the key's type. `UserDefaults` stores every number as the same
NSNumber type, so only the registry knows whether a value is an Int, Long, Float or Double. Out-of-range
integers are clamped, as today. The marker key and "legacy values stay in place" behaviour are
unchanged. A key added to the registry later needs no migration code.

Android needs no migration change: `SharedPreferencesMigration` copies every type
`SharedPreferences` holds.

### Checksum (`PrefsChecksum.kt`, common)

A 64-bit FNV-1a hash over the keys in `GeneratedKeys.all`, in name order. For each key it hashes
the name, a one-byte type tag, and the value:
- integers as big-endian bytes;
- Float and Double as their raw IEEE bits;
- strings as UTF-8;
- sets as their elements sorted, each followed by a zero byte.

The Python script implements the same function. A shared test vector proves the two agree.

### Benchmark launch mode

| | Android | iOS |
|---|---|---|
| How it is chosen | a file `files/bench_mode` written by the script, read in `Application.onCreate` (an Application cannot see intent extras) | launch argument `--bench <mode>` |
| Modes | `datastore`, `baseline` | same |

Without the flag the apps behave exactly as now.

**`datastore` mode**, at process start, before any UI:
1. Time the first access, as `UNIFIEDPREFS_LOAD` does today.
2. Compute the checksum.
3. Put one Int and time until it is saved (`flush()`).
4. Put the same String 100 times in a loop, flush, and report the time and the change in
   `saveCount`.

It then prints one line:

`UNIFIEDPREFS_BENCH mode=datastore load_ms= checksum= save_ms= burst_ms= burst_saves=`

**`baseline` mode** reads the same data from the old store, under a separate name the migration
never touches (Android: `baseline_prefs`; iOS: suite `baseline`). It times the first full read
(Android: `getSharedPreferences` plus `getAll()`; iOS: `dictionaryRepresentation()` on the suite)
and one write. It prints:

`UNIFIEDPREFS_BENCH mode=baseline load_ms= save_ms=`

Each mode runs in its own cold launch, so the two never share a process.

### Scripts

- `scripts/seed-legacy.sh <android|ios> <profile>`: writes the generated old store, and a
  baseline copy, into the installed before-state app's data. The app is stopped first.
  - Android: through `run-as`, into `shared_prefs/`.
  - iOS: through `simctl spawn … defaults import`, so the preferences daemon sees the values.
    If that does not work on the simulator, the fallback is writing the plist with the
    simulator shut down. Wave 0 of the plan proves which one works.
- `scripts/bench.sh <android|ios> <profile>`, one full run:
  1. install the before-state build and seed it;
  2. upgrade to the new build;
  3. launch once (the migration run), then five cold launches in each mode;
  4. compare the checksum with the expected one, and record file sizes;
  5. write `results/<platform>-<profile>.csv`.

## Results to report

For each platform and profile:

| Measure | Reported as |
|---|---|
| First access, DataStore | median and range of 5 cold starts, and the migration run separately |
| First access, old store | median and range of 5 cold starts |
| Migration exact | checksum match, yes or no |
| Single write until on disk | median, DataStore and old store |
| 100 quick writes | time, and the number of saves they caused |
| File size | DataStore file next to the old store's |

`FINDINGS.md` gets a "Real-app scale" section with these tables. It states plainly where DataStore
is slower than the old store, and by how much.

## Success criteria

- Checksums match after the upgrade on both platforms, in both profiles. A mismatch is a defect
  to fix before anything is reported.
- Every measure above is filled in for all four platform–profile combinations.
- Existing behaviour is unchanged: the current upgrade, fresh-install and sync checks still pass,
  and the existing test suites stay green.

There are no timing pass marks. The point is honest numbers next to a baseline, not a target
chosen after the fact.

## Testing

- **Common:**
  - get/put/observe for each new type;
  - `saveCount` counts saves and not puts;
  - the checksum matches the shared test vector.
- **Android host:** `SharedPreferencesMigration` preserves Long, Float and string-set values
  from a small generated XML fixture.
- **iOS:**
  - the registry-driven migration reads every type correctly;
  - it handles a key stored as the wrong NSNumber kind;
  - the renamed demo keys still migrate.
- **Generator self-check:** described above.
- **End to end:** `bench.sh` on both platforms and both profiles.

## Risks

- **iOS seeding:** the preferences daemon caches values and may overwrite a plist written behind
  its back. Hence `defaults import`, proved first.
- **Main-thread time:** in the heavy profile the first access blocks for longer. That is a result
  to report, not hide, and the findings should say what an app could do about it, such as
  touching storage on a background thread at start.
- **Page cache:** the old store and DataStore may both read from a warm OS file cache, which is
  also the common real case. The findings will say so.
- **Emulator numbers:** these are not phone numbers. Findings report them as relative to the
  baseline, not as absolute promises.
