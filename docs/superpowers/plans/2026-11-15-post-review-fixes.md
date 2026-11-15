# Post Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the one singleton gap in `:storage`, then fix every issue the reader review found in the Medium post, in the repository copy and in the live Medium draft, and stop before publishing.

**Architecture:**
- **Task 1** changes code: one visibility change, proven by the Swift header and the test suites.
- **Task 2** edits the post's Markdown, which is the source of truth.
- **Task 3** carries Task 2 into the Medium draft and fixes Medium-only formatting.
- **Task 4** is publishing, gated on your explicit go-ahead.

**Tech Stack:** Kotlin Multiplatform, Jetpack DataStore 1.3.0, Medium editor in your Chrome (Claude in Chrome), `gh`.

**Spec:** the reader review given in conversation on 2026-11-15, and the singleton check below. There is no separate spec file; rulings made from them are recorded in this plan.

## Singleton check: what was found

| Question | Answer | Evidence |
|---|---|---|
| One repository per process on Android? | Yes | `prefsRepository(context)` in `AndroidStorage.kt`: `@Volatile` field plus double-checked `synchronized`; `PrefsRepository`'s constructor is `internal`, so this is the only way to get one |
| One repository per process on iOS? | Yes | `object IosStorage { val repo by lazy { ... } }`; Kotlin/Native `lazy` is synchronized by default |
| Every app call site goes through those accessors? | Yes | Android `LegacyPrefs`, `MainActivity` and `PocApp` call `prefsRepository(...)`; Swift `LegacyPrefs`, `ContentView` and `PocApp` use `IosStorage.shared.repo` |
| Can anything create a second DataStore on the same file? | **Yes: a gap** | `createDataStore(...)` in `commonMain/DataStoreFactory.kt` is `public`, and the Swift header exports it as `createDataStore(migrations:scope:producePath:)`. App code on either platform could open a second store on `app.preferences_pb`. On Android that throws "multiple DataStores active for the same file" at first read; on iOS two stores would write the same file unsynchronised |
| One store across processes? | **No, by design** | DataStore Preferences is single-process. An Android app running components in another `android:process`, or an iOS widget or app extension, is a separate process with its own instance. Not used in the sample; worth saying in the post |

## Global Constraints

- No change to any public API that the native apps use: `prefsRepository`, `IosStorage.shared.repo`, `PrefKeys`, `PrefsRepository` members.
- The post stays honest: no claim beyond `FINDINGS.md`; numbers unchanged.
- Never press Publish, and never change the repository's visibility, without your explicit go-ahead in chat.
- The post must not mention the work project, the PR you reviewed, your organisation, or any excluded library vendor.

## Review Focus

1. **A second store on the same file:** after Task 1, app code cannot call `createDataStore`. Pinned by Task 1's header and compile checks.
2. **Leftover or duplicated text in the Medium draft after re-pasting:** Task 3 checks the draft's text for one occurrence of each list and no old tables.
3. **Code samples split by Medium at blank lines:** Task 2 removes blank lines inside code samples, and Task 3 counts the code blocks.
4. **Wrong syntax colouring:** Task 3 sets each code block's language by hand and checks the labels.
5. **A dead link at publish time:** Task 4 makes the repository public, checks it while signed out, then publishes.

---

### Task 1: Make the DataStore factory internal

**Files:**
- Modify: `storage/src/commonMain/kotlin/com/example/unifiedprefs/storage/DataStoreFactory.kt` (the `createDataStore` declaration)
- Modify: `FINDINGS.md` ("Design points that held up": add one line)

**Interfaces:**
- Produces: `internal fun createDataStore(migrations: List<DataMigration<Preferences>>, scope: CoroutineScope = ..., producePath: () -> String): DataStore<Preferences>`. Callers stay `androidDataStore`, `IosStorage` and `CorruptStoreTest`, all inside `:storage`.

- [ ] **Step 1: Confirm the gap.** Run
  `grep -c "createDataStoreMigrations" shared-ui/build/xcode-frameworks/Debug/*/Shared.framework/Headers/Shared.h`.
  Expected: `1`.
- [ ] **Step 2: Change `fun createDataStore(` to `internal fun createDataStore(`.**
- [ ] **Step 3: Run the storage tests and both app builds:**
  `./gradlew :storage:jvmTest :storage:testAndroidHostTest :storage:iosSimulatorArm64Test :androidApp:assembleDebug`, then `scripts/ios-install.sh`.
  Expected: all pass (JVM 31, Android host 38, iOS 41), and `BUILD SUCCEEDED`.
- [ ] **Step 4: Re-run the Step 1 grep.** Expected: `0`. The factory is no longer visible to Swift. Android app code cannot see it either, because `androidApp` is a separate module.
- [ ] **Step 5: Run the existing upgrade check on both platforms** (`scripts/bench.sh android typical 1` and `scripts/bench.sh ios typical 1`). Expected: the checksum matches. This proves the accessors still create and migrate the store.
- [ ] **Step 6: Add to FINDINGS, "Design points that held up":**
  "**One store per process is enforced, not just documented.** `PrefsRepository`'s constructor and the DataStore factory are `internal`; apps can only reach the store through `prefsRepository(context)` or `IosStorage.shared.repo`. DataStore is single-process: Android components in another process, and iOS widgets or extensions, would each get their own store."
- [ ] **Step 7: Commit** `fix(storage): make the DataStore factory internal so apps cannot open a second store`.

### Task 2: Fix the post's Markdown

**Files:**
- Modify: `docs/medium-post-draft.md`

Each step below changes one passage. The quoted text is the copy to use.

- [ ] **Step 1: Title.** Replace with: `I Put SharedPreferences and NSUserDefaults Behind One DataStore. Here’s What 200 Keys Showed.`
- [ ] **Step 2: Subtitle.** Replace with: `In a sample app, I moved both platforms’ preferences onto one Kotlin Multiplatform store without changing a single call site, then measured it against the old stores.`
- [ ] **Step 3: Add a short summary right after the subtitle's separator:**
  ```
  **In short:**
  - Every one of 200 values of every type migrated exactly, on both platforms.
  - Android start-up stayed the same at normal sizes and got about 6× faster with megabytes stored.
  - iOS start-up got about 13 ms slower than `UserDefaults`.
  ```
- [ ] **Step 4: State the audience.** At the end of the first paragraph, add: `This post assumes your apps already ship a Kotlin Multiplatform framework; adding one only for preferences is a different trade-off.`
- [ ] **Step 5: Add "Why not an existing library?"** as a paragraph after "The usual first instinct…":
  `There are good multiplatform settings libraries, and most of them do exactly that: one API over each platform's own store. I wanted to test the other end state, where both apps end up on the same store and file format, so a value only ever has one home.`
- [ ] **Step 5b: Add "Why DataStore?"** as a short section right before "The idea in one picture":
  ```
  ### Why DataStore?

  I wanted the end state to depend on as little as possible:

  - **It is AndroidX.** Google maintains it, and its Preferences flavour officially supports Kotlin Multiplatform, Android and iOS included.
  - **Half the migration already exists.** DataStore ships `SharedPreferencesMigration`, so only iOS needed a migration written by hand.
  - **It covers the types both old stores hold:** Boolean, Int, Long, Float, Double, String and string sets.
  - **Writes are atomic.** It writes a temporary file and renames it, so a crash mid-save leaves the old file intact rather than a half-written one.
  - **No new native library.** On iOS it runs as Kotlin inside the framework the app already ships.

  What I set aside:
  - **a wrapper over each platform's store** (it keeps two stores);
  - **a native memory-mapped key-value store** (fast, but another native dependency to vet and maintain on both platforms);
  - **a database such as SQLDelight or Room** (more than key-value preferences need);
  - **a hand-written file format** (I would be rebuilding what DataStore already does).
  ```
  Constraint: name no vendor of a library the team excluded; describe alternatives by kind, as above.

- [ ] **Step 6: Address `runBlocking` next to the repository snippet.** After the paragraph that starts "The saver runs on a single-threaded scope", add:
  `Yes, that is runBlocking, and the first access happens at app start on the main thread in this sample. It blocks once: about 110–120 ms on the Android emulator and 14–52 ms on the iOS simulator in my runs, and longer on the one launch that migrates. If that matters for your start-up, warm the store up on a background thread before the first read; more on that below.`
- [ ] **Step 7: Add singleton and process scope after rule 1 of "Four rules":**
  `The store's factory and the repository's constructor are internal, so the only way in is one accessor per platform. That makes "one instance per process" something the compiler enforces. DataStore is single-process, though: an Android component in another process, or an iOS widget or extension, gets its own copy.`
  Add `multi-process apps` to the "Not covered" bullet.
- [ ] **Step 8: Remove blank lines inside every code sample**, so Medium keeps each sample as one block. Join the parts with a single newline; nothing else changes.
- [ ] **Step 9: Narrow the diagram to at most 40 characters per line** (stack the three callers vertically), so it does not scroll sideways on a phone. Check with:
  `python3 -I -c "import re;t=open('docs/medium-post-draft.md').read();d=t.split('\`\`\`')[1];print(max(len(l) for l in d.splitlines()))"`.
  Expected: ≤ 40.
- [ ] **Step 10: Run a fact check.** Every number in the post must appear in FINDINGS: `grep -oE "[0-9.]+ ?(ms|KB|MB|×)" docs/medium-post-draft.md | sort -u`, each one checked against the FINDINGS tables. Expected: no number missing from FINDINGS.
- [ ] **Step 11: Commit** `docs(post): review fixes: audience, why-not-a-library, runBlocking, singleton scope, summary`, and push to `origin main`.

### Task 3: Carry the changes into the Medium draft

**Files:** none in the repo. Live draft: `https://medium.com/p/e9a602488364/edit`, in your Chrome.

- [ ] **Step 1:** Regenerate the HTML with `md2medium.py` from the Task 2 Markdown.
- [ ] **Step 2: Replace the whole body in one paste.**
  1. Put the cursor in the title.
  2. Select everything in the story with Cmd+A.
  3. Paste the full HTML once.

  One paste over a full selection avoids the leftovers the piecemeal replacement left last time.
- [ ] **Step 3: Check the text.** In the draft's `innerText`:
  - "Every value arrived exactly", "70 Booleans" and "Android (typical" each appear exactly once;
  - "What they look like" appears 0 times;
  - the number of `PRE` blocks equals the number of code samples in the Markdown.
- [ ] **Step 4: Set each code block's language** from the dropdown under it:
  - the diagram: none / plain text;
  - Kotlin samples: Kotlin;
  - the Swift sample: Swift.

  Expected: no "Auto (…)" label left showing SCSS, CSS or Less.
- [ ] **Step 5:** Take a screenshot of the top, the numbers section and the diagram, and confirm Medium shows "Saved".

### Task 4: Publish, only on your go-ahead

- [ ] **Step 1: Ask you** to confirm three things:
  - make the repository public;
  - the tags: Kotlin Multiplatform, Android, iOS, Jetpack Compose, Mobile Development;
  - press Publish.

  A cover image is your choice; Medium's publish dialog offers one from the story.
- [ ] **Step 2:** On a yes, run `gh repo edit thiyagatrev/unified-prefs --visibility public --accept-visibility-change-consequences`. Then confirm the repo page loads for a signed-out visitor, with `curl -s -o /dev/null -w "%{http_code}" https://github.com/thiyagatrev/unified-prefs` returning `200`.
- [ ] **Step 3:** Open Publish in Medium, add the tags, and press Publish. Report the public URL.
