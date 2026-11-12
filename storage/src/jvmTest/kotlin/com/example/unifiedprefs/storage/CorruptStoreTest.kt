package com.example.unifiedprefs.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CorruptStoreTest {
    @Test
    fun unreadableFileIsReplacedWithEmptyPreferences() = runTest {
        val file = File.createTempFile("corrupt", ".preferences_pb")
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })
        val store = createDataStore(emptyList()) { file.absolutePath }
        assertTrue(store.data.first().asMap().isEmpty())
    }
}
