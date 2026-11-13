# One Preferences Store for Android, iOS and Compose Multiplatform

### I moved SharedPreferences and NSUserDefaults onto a single DataStore without touching a single call site. Then I loaded it with 200 real-looking keys to see where it breaks.

---

If you are moving an existing Android app and an existing iOS app towards Kotlin Multiplatform, preferences are one of the first things that hurt. Both apps already have years of user data in them: a dark-mode flag, an onboarding state, a cached token, a launch counter. The Android side reads it from `SharedPreferences`, the iOS side from `NSUserDefaults`, and the new shared Compose screens need to read and write the same values.

The usual first instinct is to keep both stores and put a common interface over them. That works, but you still have two stores, two sets of key names, two serialisation formats and two migration stories, forever.

I wanted to know whether there was a simpler end state: **one store, one list of keys, and the existing native code left exactly as it is.** So I built a small sample app on both platforms to test the idea, and then pushed it with a realistic amount of data. This post is what I found, including the parts that did not go my way.

---

## The idea in one picture

```
 Native Android code      Native iOS code       Shared Compose UI
 (LegacyPrefs.kt)         (LegacyPrefs.swift)   (SettingsScreen)
        │                        │                      │
        └──────── same getters and setters ─────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   :storage (KMP module) │
                    │  PrefKeys   (registry)  │
                    │  PrefsRepository        │
                    │   └ in-memory snapshot  │
                    │   └ background saver    │
                    │  Jetpack DataStore      │
                    └─────────────────────────┘
```

Four rules:

1. **One KMP module owns the storage.** It contains one Jetpack DataStore (Preferences) per app, created once per process.
2. **Reads come from memory and are synchronous.** Native code reads preferences synchronously at app start; DataStore only offers a `Flow`. So the repository loads the file once, keeps a snapshot in memory, and answers every `get` from that snapshot.
3. **Writes update memory immediately and save in the background.** A `put` is visible to the next `get` at once, just like `SharedPreferences.apply()`.
4. **Keys can only be declared in one place.** Every key is a typed object in a registry inside the storage module. Nothing outside the module can invent a key or pass a raw string.

The existing native preference classes keep their public API. Only their insides change.

---

## The pieces

### A key registry nobody can bypass

```kotlin
sealed class PrefKey(val name: String, val legacyIosName: String)

class BoolKey internal constructor(
    name: String, val default: Boolean, legacyIosName: String = name,
) : PrefKey(name, legacyIosName) {
    internal val key = booleanPreferencesKey(name)
}
// IntKey, LongKey, FloatKey, DoubleKey, StringKey, StringSetKey look the same

object PrefKeys {
    val darkMode    = BoolKey("dark_mode", false, legacyIosName = "isDarkMode")
    val launchCount = IntKey("launch_count", 0, legacyIosName = "launchCount")
    val userName    = StringKey("user_name", "", legacyIosName = "userName")
}
```

The constructors are `internal`. Android code, iOS code and shared UI can *use* `PrefKeys.darkMode`, but they cannot create a new key. Defaults live on the key, so the two platforms can never disagree about what "unset" means. `legacyIosName` records what the iOS app used to call the same value, which matters for the migration below.

This one detail turned out to be the most useful part of the design. It turns "please declare keys in the storage layer" from a team convention into a compiler error.

### A repository that answers synchronously

```kotlin
class PrefsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    // Blocks once, on first access. Every read after this is from memory.
    private val state = MutableStateFlow(Snapshot(runBlocking { dataStore.data.first() }, 0))

    fun getBoolean(key: BoolKey) = state.value.prefs[key.key] ?: key.default
    fun putBoolean(key: BoolKey, value: Boolean) = write { it[key.key] = value }
    fun observeBoolean(key: BoolKey): Flow<Boolean> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()
    // ... the same for the other six types
}
```

The saver runs on a single-threaded scope and always writes the *latest whole snapshot*, not individual changes. That makes save order irrelevant: if ten writes pile up, the next save writes the state after all ten. A failed save is counted and retried by the next one; it never crashes the app.

### Native code does not change

Android, before and after, from the caller's point of view:

```kotlin
class LegacyPrefs(context: Context) {
    private val repo = prefsRepository(context)   // was: context.getSharedPreferences(...)

    var darkMode: Boolean
        get() = repo.getBoolean(PrefKeys.darkMode)
        set(value) = repo.putBoolean(PrefKeys.darkMode, value)
}
```

iOS is the same idea in Swift, calling the Kotlin framework:

```swift
var isDarkMode: Bool {
    get { repo.getBoolean(key: PrefKeys.shared.darkMode) }
    set { repo.putBoolean(key: PrefKeys.shared.darkMode, value: newValue) }
}
```

Every existing `prefs.darkMode = true` in both apps keeps compiling and keeps working.

### The one-time migrations

On **Android** DataStore already ships `SharedPreferencesMigration`:

```kotlin
PreferenceDataStoreFactory.createWithPath(
    migrations = listOf(SharedPreferencesMigration(context, "app_prefs")),
    produceFile = { /* filesDir/datastore/app.preferences_pb */ },
)
```

On **iOS** there is nothing built in, so I wrote a `DataMigration` that walks the key registry and reads each value from `NSUserDefaults` by the key's *type*:

```kotlin
for (key in PrefKeys.allKeys) {
    if (defaults.objectForKey(key.legacyIosName) == null) continue
    when (key) {
        is BoolKey   -> out[key.key] = defaults.boolForKey(key.legacyIosName)
        is LongKey   -> out[key.key] = defaults.integerForKey(key.legacyIosName)
        is FloatKey  -> out[key.key] = defaults.floatForKey(key.legacyIosName)
        // ... Int (clamped), Double, String, Set<String>
    }
}
out[migratedMarker] = true
```

Walking the registry matters because `NSUserDefaults` stores every number as the same `NSNumber`. Only the registry knows whether `launchCount` was meant to be an `Int` or a `Long`. It also means a key added next year needs no migration code at all.

---

## Did it work?

With the three demo keys, yes, on both the Android emulator and the iOS simulator:

- **Upgrade over an existing install:** dark mode on, name and launch count all arrived; the count kept going 3 → 4 → 5.
- **Fresh install:** defaults everywhere.
- **Native and shared UI stay in sync**, both directions, on both platforms.

But three keys prove almost nothing about a real app. So I generated **200 preferences** of every type:

| Type | Count | What they look like |
|---|---|---|
| Boolean | 70 | feature flags |
| Int | 40 | counters, including `Int.MIN_VALUE` and `MAX_VALUE` |
| Long | 24 | millisecond timestamps, plus the `Long` extremes |
| Float | 16 | ratios |
| Double | 8 | coordinates (iOS only: `SharedPreferences` has no Double) |
| Short strings | 30 | labels, including empty, emoji, Tamil, `<`, `&`, quotes, newlines |
| Medium strings | 6 | 1–4 KB tokens and small JSON |
| Large strings | 2 | cached JSON responses |
| String sets | 4 | 3–50 tags each |

I ran it in two profiles over the same keys: a **typical** app (about 77 KB of values) and a **heavy** app (about 2.4 MB, the app that caches API responses in preferences; we have all seen one).

For each run a script installed the old build, wrote the old store, **launched the old build once so Android and iOS rewrote that file in their own format**, upgraded to the new build, and then made 11 cold starts measuring DataStore and 11 measuring the old store reading the same data.

### Every value arrived exactly

After every migration, a checksum over all 200 values matched the expected one, on both platforms, in both profiles, on all 48 launches. Extreme integers, 32-bit floats, emoji, Tamil text, XML special characters and megabyte-sized JSON all came through byte for byte.

That was the result I cared about most. A preferences migration that quietly rounds a float or drops a key is far worse than a slow one.

### The numbers

Medians, Debug builds, Android emulator and iOS simulator. Lower is better.

| | Android typical | Android heavy | iOS typical | iOS heavy |
|---|---|---|---|---|
| First access, DataStore | 121 ms | **110 ms** | 14 ms | 52 ms |
| First read, old store | 112 ms | **677 ms** | 4.3 ms | 5.7 ms |
| One write, DataStore | 55 ms | 123 ms | 11 ms | 73 ms |
| One write, old store | 15 ms | 61 ms | — * | — * |
| Migration launch (once) | 163 ms | 776 ms | 20 ms | 65 ms |
| File on disk, DataStore vs old | 82 vs 142 KB | 2.4 vs 4.3 MB | 82 vs 149 KB | 2.4 vs 4.8 MB |

\* `UserDefaults` hands writes to a separate system process, so its write time is not comparable.

What I take from this:

**On Android at normal sizes, start-up is a wash.** 121 ms against 112 ms is inside the noise. Creating the key objects adds about 13 ms.

**On Android at heavy sizes, DataStore is about six times faster to start.** Parsing 4.3 MB of XML is slower than reading 2.4 MB of protobuf. DataStore won every one of the eleven runs.

**On iOS, DataStore is slower than `UserDefaults` at start-up**: about 10 ms more at normal sizes, about 45 ms more in the heavy profile, plus 2–4 ms to create the key objects. `UserDefaults` is served by a system daemon that keeps your preferences in memory between launches; DataStore reads its file in your process every time. The Kotlin/Native code in this test was also a Debug build.

**Each write is slower, but nobody waits for it.** Both DataStore and `SharedPreferences` rewrite the whole file on every save. DataStore took 2–4× longer per save in these runs. The value is in memory the moment you set it, so the UI never waits. And when writes come quickly, they are batched: 100 writes in a row, like someone typing, needed only 2–9 save passes.

**The migration costs one slower launch, once.** On Android at heavy size, that one launch takes most of a second.

### The limit I did not expect

In the heavy profile, the old iOS plist was 4.8 MB on disk. Every write to it made iOS log this, at fault level:

> Attempting to store >= 4194304 bytes of data in CFPreferences/NSUserDefaults on this platform is invalid. This is a bug in [your app] or a library it uses.

The values were only 2.4 MB, but a binary plist stores any string with a single non-ASCII character as UTF-16. A few Tamil words in a cached JSON response were enough to double its size and cross the line. On the simulator the writes were still kept, but "invalid" is not something I would ship on. DataStore held the same values in 2.4 MB with no complaint.

---

## What I would tell a team adopting this

**Do it for the single source of truth, not for speed.** At normal sizes the performance difference is small either way. What you gain is one store, one key list, typed synchronous reads on every platform, and a migration that copies values exactly.

**Get the keys into a registry first.** Even if you never adopt DataStore, a typed key list with `internal` constructors removes a whole class of bugs: two platforms with different names or different defaults for the same setting.

**Keep the registry readable as it grows.** 200 keys in one Kotlin `object` is fine. Be careful with the advice you may hear about a "255 limit": that is the JVM's limit on *parameters*, which rules out a data class holding hundreds of keys, not an `object`. In my tests an object with 1000 keys compiled on Android, JVM and iOS. The real reason to split is people: past a few hundred keys, one file means merge conflicts and keys nobody dares delete. Then split them into one `object` per feature, still inside the storage module so the constructors stay `internal`, with one combined list for the iOS migration. Callers do not notice: native code still uses its getters and setters, and shared code uses the keys.

**Do not keep megabytes in preferences, whichever store you use.** That was already true with `SharedPreferences` and `NSUserDefaults`; the heavy profile just makes it obvious. Every save rewrites the whole file, the migration launch gets slow, and on iOS you can cross a size Apple calls invalid. Cached responses belong in files or a database.

**If start-up time matters, warm the store up early on a background thread.** For example, call the repository accessor from a thread you start in `Application.onCreate`, or from a background queue at iOS launch. It only helps if your first synchronous read comes after the warm-up has started.

**Know the two migration traps:**

- **Android:** `SharedPreferencesMigration` deletes the keys it copies. That deletion is what stops it running twice, but it also means a rollback to an old build starts from empty.
- **iOS:** the migration runs once and then sets a marker. In my tests the iOS simulator sometimes had not yet written recent `NSUserDefaults` values to disk when the app was reinstalled, so the migration saw fewer keys than the user had. Keys missing at that moment are never copied later. I did not test whether a real App Store update can hit the same thing, but I would want to know before shipping.

---

## What this test does not prove

I want to be straight about the limits:

- **Emulator and simulator only, and Debug builds only.** Debug slows down DataStore but not `SharedPreferences` or `UserDefaults`, which are system code. So these numbers are the worst case for DataStore. I would measure Release builds on real phones before quoting absolute differences.
- **The test ran on a busy laptop.** I waited for the machine to go quiet before each run and threw away runs made under heavy load, but this is not a lab.
- **Not covered:** encryption, iOS app groups and extensions, memory use, real store updates, and a process killed in the middle of a save.

---

## Wrapping up

The question I started with was whether one KMP storage module could sit under two existing native apps without anyone changing a call site and without losing a single user's data. On an emulator and a simulator, with 200 realistic keys, the answer was yes. Every value arrived exactly, Android start-up was the same or better, and iOS paid about 13 ms at normal sizes.

The bigger lesson was not about DataStore at all. It was the registry: making the compiler, not a code review, the thing that decides where a preference key may be declared.

The sample app, the generator, the benchmark scripts and the raw results are on GitHub: **[link to the sample repository]**.

If you have tried something similar, especially on real devices or with Release builds, I would love to compare numbers.
