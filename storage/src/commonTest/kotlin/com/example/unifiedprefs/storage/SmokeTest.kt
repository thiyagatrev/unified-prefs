package com.example.unifiedprefs.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun registryHasThreeKeys() {
        assertEquals(3, PrefKeys.allNames.size)
    }
}
