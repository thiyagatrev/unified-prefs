package com.example.unifiedprefs.storage

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import okio.FileSystem

/** AES-256-GCM, random 12-byte IV prepended. On Android the key would live in Android Keystore. */
private class AesGcmCipher(private val key: SecretKey) : PrefsCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(plain)
    }

    override fun decrypt(cipher: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, cipher.copyOfRange(0, 12))) }
        return c.doFinal(cipher, 12, cipher.size - 12)
    }
}

class AesGcmEncryptedStoreTest {
    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun aesGcmStoreHidesPlaintextAndRejectsTheWrongKey() = runBlocking {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "aes-${Random.nextLong()}.preferences_pb"
        val key = newKey()
        val job = SupervisorJob()
        encryptedDataStore(path, AesGcmCipher(key), CoroutineScope(Dispatchers.Default + job))
            .edit { it[PrefKeys.userName.key] = "token-like-value-9876" }
        job.cancelAndJoin()

        val onDisk = FileSystem.SYSTEM.read(path) { readByteArray() }
        assertFalse(onDisk.decodeToString().contains("token-like-value-9876"))

        val job2 = SupervisorJob()
        val same = encryptedDataStore(path, AesGcmCipher(key), CoroutineScope(Dispatchers.Default + job2)).data.first()
        assertEquals("token-like-value-9876", same[PrefKeys.userName.key])
        job2.cancelAndJoin()

        // Wrong key: GCM's tag check fails instead of returning garbage.
        val job3 = SupervisorJob()
        assertFailsWith<Throwable> {
            encryptedDataStore(path, AesGcmCipher(newKey()), CoroutineScope(Dispatchers.Default + job3)).data.first()
        }
        job3.cancelAndJoin()
        FileSystem.SYSTEM.delete(path)
    }
}
