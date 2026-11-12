package com.example.unifiedprefs.storage

import kotlin.test.Test
import kotlin.test.assertTrue

class IosSmokeTest {
    @Test
    fun storePathEndsWithTheStoreFileName() {
        assertTrue(iosStorePath().endsWith("/app.preferences_pb"))
    }
}
