package com.example.unifiedprefs.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path

/** Byte-level cipher a platform supplies (Android Keystore AES-GCM, iOS CryptoKit via Swift, ...). */
interface PrefsCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(cipher: ByteArray): ByteArray
}

/** Wraps DataStore's own Preferences serializer so only ciphertext reaches the file. */
class EncryptingPreferencesSerializer(private val cipher: PrefsCipher) : OkioSerializer<Preferences> {
    override val defaultValue: Preferences get() = PreferencesSerializer.defaultValue

    override suspend fun readFrom(source: BufferedSource): Preferences =
        PreferencesSerializer.readFrom(Buffer().write(cipher.decrypt(source.readByteArray())))

    override suspend fun writeTo(t: Preferences, sink: BufferedSink) {
        val plain = Buffer().also { PreferencesSerializer.writeTo(t, it) }.readByteArray()
        sink.write(cipher.encrypt(plain))
    }
}

fun encryptedDataStore(path: Path, cipher: PrefsCipher, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, EncryptingPreferencesSerializer(cipher)) { path },
        scope = scope,
    )

/** Test-only stand-in for a real cipher: proves the hook on every target, not cryptographic strength. */
private class XorCipher(private val key: Byte) : PrefsCipher {
    override fun encrypt(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor key.toInt()).toByte() }
    override fun decrypt(cipher: ByteArray) = encrypt(cipher)
}

class EncryptedStoreTest {
    private val k = PrefKeys

    @Test
    fun encryptedStoreRoundTripsEveryTypeAndHidesPlaintext() = runBlocking {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "enc-${Random.nextLong()}.preferences_pb"
        val secretName = "secret-user-name-1234"
        val job = SupervisorJob()
        val store = encryptedDataStore(path, XorCipher(0x5A), CoroutineScope(Dispatchers.Default + job))
        store.edit {
            it[k.userName.key] = secretName
            it[k.launchCount.key] = 42
            it[k.darkMode.key] = true
        }
        job.cancelAndJoin()

        val onDisk = FileSystem.SYSTEM.read(path) { readByteArray() }
        assertFalse(onDisk.decodeToString().contains(secretName), "plaintext found in the file")

        val job2 = SupervisorJob()
        val reopened = encryptedDataStore(path, XorCipher(0x5A), CoroutineScope(Dispatchers.Default + job2)).data.first()
        assertEquals(secretName, reopened[k.userName.key])
        assertEquals(42, reopened[k.launchCount.key])
        assertEquals(true, reopened[k.darkMode.key])
        job2.cancelAndJoin()
        FileSystem.SYSTEM.delete(path)
    }
}
