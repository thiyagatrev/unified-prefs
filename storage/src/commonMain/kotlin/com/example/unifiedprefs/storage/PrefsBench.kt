package com.example.unifiedprefs.storage

import kotlinx.coroutines.runBlocking
import kotlin.math.floor
import kotlin.time.TimeSource

/** Contract R5. */
class BenchResult(val checksum: String, val saveMs: Double, val burstMs: Double, val burstSaves: Int)

/** Contract R5. POC benchmark; writes only [PrefKeys.benchInt] and [PrefKeys.benchText]. */
object PrefsBench {
    /** Runs on the calling thread and blocks. */
    fun measure(repo: PrefsRepository): BenchResult {
        val checksum = repo.checksum()

        val saveStart = TimeSource.Monotonic.markNow()
        repo.putInt(PrefKeys.benchInt, repo.getInt(PrefKeys.benchInt) + 1)
        runBlocking { repo.flush() }
        val saveMs = saveStart.elapsedNow().inWholeNanoseconds / 1_000_000.0

        val savesBefore = repo.saveCount.value
        val burstStart = TimeSource.Monotonic.markNow()
        for (i in 0..99) repo.putString(PrefKeys.benchText, "bench-$i")
        runBlocking { repo.flush() }
        val burstMs = burstStart.elapsedNow().inWholeNanoseconds / 1_000_000.0

        return BenchResult(checksum, saveMs, burstMs, repo.saveCount.value - savesBefore)
    }

    fun datastoreLine(loadMs: Double, keysMs: Double, r: BenchResult): String =
        "UNIFIEDPREFS_BENCH mode=datastore load_ms=${fmt(loadMs)} keys_ms=${fmt(keysMs)} " +
            "checksum=${r.checksum} save_ms=${fmt(r.saveMs)} burst_ms=${fmt(r.burstMs)} burst_saves=${r.burstSaves}"

    fun baselineLine(loadMs: Double, saveMs: Double): String =
        "UNIFIEDPREFS_BENCH mode=baseline load_ms=${fmt(loadMs)} save_ms=${fmt(saveMs)}"

    /** One decimal place, half up, no String.format in common code. */
    private fun fmt(x: Double): String {
        val tenths = floor(x * 10 + 0.5).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }
}
