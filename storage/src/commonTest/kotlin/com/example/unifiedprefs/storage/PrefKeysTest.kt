package com.example.unifiedprefs.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class PrefKeysTest {

    @Test
    fun allStoredNamesAreUnique() {
        val allNames = PrefKeys.allNames
        assertEquals(allNames.toSet().size, allNames.size)
    }

    @Test
    fun storedNamesMatchTheKeysTable() {
        assertEquals(
            listOf("dark_mode", "launch_count", "user_name", "bench_int", "bench_text"),
            PrefKeys.allNames.take(5),
        )
        assertEquals(5 + GeneratedKeys.all.size, PrefKeys.allNames.size)
    }
}
