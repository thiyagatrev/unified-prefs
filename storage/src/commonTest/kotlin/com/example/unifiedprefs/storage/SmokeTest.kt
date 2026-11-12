package com.example.unifiedprefs.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun registryHasHandWrittenAndGeneratedKeys() {
        assertEquals(5 + GeneratedKeys.all.size, PrefKeys.allNames.size)
    }
}
