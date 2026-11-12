package com.example.unifiedprefs.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefsBenchTest {
    private fun repo() = PrefsRepository(FakeDataStore(), defaultStorageScope())

    @Test
    fun measureReportsChecksumOfGeneratedKeys() {
        val repo = repo()
        val expected = repo.checksum()
        assertEquals(expected, PrefsBench.measure(repo).checksum)
    }

    @Test
    fun measureLeavesGeneratedValuesUntouched() {
        val repo = repo()
        val before = repo.checksum()
        PrefsBench.measure(repo)
        assertEquals(before, repo.checksum())
    }

    @Test
    fun measureIncrementsBenchInt() {
        val repo = repo()
        assertEquals(0, repo.getInt(PrefKeys.benchInt))
        PrefsBench.measure(repo)
        assertEquals(1, repo.getInt(PrefKeys.benchInt))
        assertEquals("bench-99", repo.getString(PrefKeys.benchText))
    }

    @Test
    fun burstSavesIsAtLeastOne() {
        assertTrue(PrefsBench.measure(repo()).burstSaves >= 1)
    }

    @Test
    fun linesHaveExactFormat() {
        assertEquals(
            "UNIFIEDPREFS_BENCH mode=datastore load_ms=12.3 keys_ms=0.8 checksum=00000000000000ff save_ms=3.0 burst_ms=4.6 burst_saves=2",
            PrefsBench.datastoreLine(12.34, 0.75, BenchResult("00000000000000ff", 3.0, 4.56, 2)),
        )
        assertEquals(
            "UNIFIEDPREFS_BENCH mode=baseline load_ms=1.0 save_ms=2.3",
            PrefsBench.baselineLine(1.0, 2.25),
        )
    }
}
