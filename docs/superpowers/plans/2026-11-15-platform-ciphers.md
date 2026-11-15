# Platform Ciphers Implementation Plan (draft)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove an encrypted DataStore works with real platform crypto: Android Keystore AES-GCM and iOS CryptoKit AES-GCM, both behind the existing `PrefsCipher` interface, with no expect/actual.

**Architecture:**
- **Common code:** `PrefsCipher` and the encrypting serializer move from test code into `:storage` commonMain. The internal factory gains an optional cipher.
- **Android:** implements the cipher in Kotlin (androidMain).
- **iOS:** implements it in Swift, in the app, and passes it into Kotlin.
- **Verification:** an `encryption` launch mode in each app prints one result line, the same way the bench mode does. The app's own preferences store stays unencrypted.

**Tech Stack:** Kotlin Multiplatform, DataStore 1.3.0 (`OkioStorage`, `PreferencesSerializer`), Android Keystore + `javax.crypto`, Swift CryptoKit + Keychain (Security framework).

**Spec:** the conversation of 2026-11-15 (pure-Kotlin crypto rejected; use platform crypto behind an interface), and `FINDINGS.md`, section "Encryption: possible, hook verified".

## Global Constraints

- No expect/actual. No new third-party dependency (no crypto library).
- Cipher: AES-256-GCM, a fresh random 12-byte IV per encryption, prepended to the ciphertext, 128-bit tag.
- Keys never leave platform storage: Android Keystore alias `unifiedprefs_aes`; iOS Keychain generic password, service `com.example.unifiedprefs`, account `prefs_aes_key`, accessibility `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- The app's own `app.preferences_pb` stays plain. The encrypted store used for the check is `enc-check.preferences_pb`, deleted after the check.
- Without the launch flag, both apps behave exactly as now.
- Commits are under the author's name only, with no Claude co-author line.

## Review Focus

1. **IV reuse.** Two encryptions of the same bytes must differ (Task 1 test `encryptTwiceGivesDifferentCiphertext`, on both platforms via the check line).
2. **Wrong or lost key.** Reading with another key must fail loudly, not return data. The real-world consequence (the corruption handler would replace the store with empty preferences) is documented, not changed (Task 4).
3. **First launch with no key yet.** The key is created on first use and reused afterwards (the check runs twice: `key_reused=true` on the second run).
4. **No plaintext on disk** for any value type (check line `plaintext_on_disk=false`).
5. **A new or empty file** returns default values, not a decrypt error (covered by DataStore: no file means the serializer is never called; asserted in the Task 1 test).

---

### Task 1: Move the encryption hook into `:storage`

**Files:**
- Create: `storage/src/commonMain/kotlin/com/example/unifiedprefs/storage/PrefsCipher.kt`, holding `PrefsCipher` (public, so Swift can implement it) and `internal class EncryptingPreferencesSerializer`, both moved unchanged from `EncryptedStoreTest.kt`.
- Create: `storage/src/commonMain/kotlin/com/example/unifiedprefs/storage/EncryptionCheck.kt`.
- Modify: `storage/src/commonTest/.../EncryptedStoreTest.kt`, which now uses the moved types.

**Interfaces:**
- Produces:
  - `interface PrefsCipher { fun encrypt(plain: ByteArray): ByteArray; fun decrypt(cipher: ByteArray): ByteArray }`
  - `object EncryptionCheck { fun run(cipher: PrefsCipher, path: String): String }`. It writes `user_name="enc-check-secret"`, `launch_count=7` and `dark_mode=true` to an encrypted store at `path`, closes it, reads the raw file, reopens with the same cipher, then deletes the file. It returns:
    `UNIFIEDPREFS_ENC roundtrip=<bool> plaintext_on_disk=<bool> iv_unique=<bool>`
    where `iv_unique` encrypts the same 32 bytes twice and compares the two outputs.

- [ ] **Step 1:** Add the test `encryptionCheckReportsSuccessWithXorCipher` to `EncryptedStoreTest`: `EncryptionCheck.run(XorCipher, tempPath)` must contain `roundtrip=true plaintext_on_disk=false`. Also add `encryptTwiceGivesDifferentCiphertext`, which **must fail with XorCipher**: assert the line says `iv_unique=false`, proving the check detects a deterministic cipher. Add `missingFileGivesDefaults`: a fresh path read through the encrypted store returns empty preferences.
- [ ] **Step 2:** Run `./gradlew :storage:jvmTest --tests '*EncryptedStore*'`. Expected: FAIL, `EncryptionCheck` unresolved.
- [ ] **Step 3:** Move the two types and implement `EncryptionCheck.run`, building the store with `PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, EncryptingPreferencesSerializer(cipher)) { path.toPath() }, scope = …)` and cancelling the scope before the raw read and before reopening.
- [ ] **Step 4:** Run `./gradlew :storage:jvmTest :storage:testAndroidHostTest :storage:iosSimulatorArm64Test`. Expected: all pass. Confirm the Swift header lists `PrefsCipher` and `EncryptionCheck` after `scripts/ios-install.sh`.
- [ ] **Step 5:** Commit `feat(storage): opt-in encrypted store hook and encryption check`.

### Task 2: Android Keystore cipher

**Files:**
- Create: `storage/src/androidMain/kotlin/com/example/unifiedprefs/storage/KeystoreAesGcmCipher.kt`
- Modify: `androidApp/src/main/kotlin/com/example/unifiedprefs/PocApp.kt`, adding bench mode `encryption`.

**Interfaces:**
- Produces `class KeystoreAesGcmCipher(alias: String = "unifiedprefs_aes") : PrefsCipher`.
- Consumes `EncryptionCheck.run`.

- [ ] **Step 1:** Implement it. Get or create an AES-256 key in `AndroidKeyStore` (`KeyGenParameterSpec`, purposes ENCRYPT|DECRYPT, block mode GCM, no padding). Encrypt with `Cipher.getInstance("AES/GCM/NoPadding")` initialised with only the key: Keystore generates the IV, so read it back with `cipher.iv` and prepend it. Decrypt with `GCMParameterSpec(128, iv)`.
- [ ] **Step 2:** In `PocApp`, when the mode file says `encryption`, log `EncryptionCheck.run(KeystoreAesGcmCipher(), File(filesDir, "enc-check.preferences_pb").path)` plus ` key_reused=<true if the alias existed before this run>`, before the normal start.
- [ ] **Step 3: Verify on the emulator.** Robolectric has no AndroidKeyStore, so a host test can't cover this.
  1. Run `scripts/android-install.sh`.
  2. Write the mode file: `adb shell "run-as com.example.unifiedprefs sh -c 'mkdir -p files; echo encryption > files/bench_mode'"`.
  3. Launch twice with `scripts/android-launch.sh`, then read the result: `adb logcat -d -s UnifiedPrefs:I | grep UNIFIEDPREFS_ENC`.
  4. Expected: `roundtrip=true plaintext_on_disk=false iv_unique=true`, with `key_reused=false` on the first launch and `true` on the second.
  5. Remove the mode file.
- [ ] **Step 4:** Commit `feat(android): Keystore AES-GCM cipher with an encryption check mode`.

### Task 3: iOS CryptoKit cipher in Swift

**Files:**
- Create: `iosApp/iosApp/CryptoKitCipher.swift`
- Modify: `iosApp/iosApp/PocApp.swift`, adding `--bench encryption`.

**Interfaces:**
- Produces `final class CryptoKitCipher: NSObject, PrefsCipher` (the Swift name of the Kotlin interface comes from `Shared.h`).
- Consumes `EncryptionCheck.shared.run(cipher:path:)`.

- [ ] **Step 1:** Implement it. Load the 32-byte key from the Keychain with the Global Constraints attributes, or create a `SymmetricKey(size: .bits256)` and store it. Encrypt with `AES.GCM.seal(data, using: key)`, returning `nonce + ciphertext + tag` (`sealedBox.combined`). Decrypt with `AES.GCM.open(AES.GCM.SealedBox(combined:), using:)`. Convert between `KotlinByteArray` and `Data` at the boundary.
- [ ] **Step 2:** In `PocApp.init`, for `--bench encryption`, print the check line for `<Application Support>/enc-check.preferences_pb`, plus ` key_reused=<bool>`.
- [ ] **Step 3: Verify on the simulator.**
  1. Run `scripts/ios-install.sh`.
  2. Run `scripts/ios-launch.sh --bench encryption` twice, reading the full console output: the script greps only the state line, so use the bench launcher pattern.
  3. Expected: `roundtrip=true plaintext_on_disk=false iv_unique=true`, with `key_reused` false then true.
- [ ] **Step 4:** Commit `feat(ios): CryptoKit AES-GCM cipher with an encryption check mode`.

### Task 4: Record the result

**Files:** `FINDINGS.md`, `docs/medium-post-draft.md`, the live Medium story.

- [ ] **Step 1:** In FINDINGS, "Encryption", replace "Not tested: Android Keystore … iOS cipher" with the observed check lines. Keep "the cost of encryption" and "migrating an existing plain store" as not covered. Add the lost-key consequence: with the current corruption handler, an unreadable store is replaced by empty preferences.
- [ ] **Step 2:** In the post, change "Only the AES-GCM path on the JVM is tested here; the platform key storage is not." to say that Android Keystore and iOS CryptoKit were verified on the emulator and simulator, not on devices. Remove "platform key storage for encryption" from "Not covered".
- [ ] **Step 3:** Run the three test suites, commit `docs: platform ciphers verified on emulator and simulator`, push, then apply the two sentence changes to the live story and republish.

## Not in this plan, by decision

- Encrypting the sample app's own store, including the plain-to-encrypted migration and lost-key handling.
- Measuring encryption cost.
- Physical devices.
