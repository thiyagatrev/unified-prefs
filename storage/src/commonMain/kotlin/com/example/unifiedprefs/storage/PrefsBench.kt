package com.example.unifiedprefs.storage

/** Contract R5. */
class BenchResult(val checksum: String, val saveMs: Double, val burstMs: Double, val burstSaves: Int)

/** Contract R5. POC benchmark; writes only [PrefKeys.benchInt] and [PrefKeys.benchText]. */
object PrefsBench {
    /** Runs on the calling thread and blocks. */
    fun measure(repo: PrefsRepository): BenchResult = TODO("WP-5")

    fun datastoreLine(loadMs: Double, r: BenchResult): String = TODO("WP-5")

    fun baselineLine(loadMs: Double, saveMs: Double): String = TODO("WP-5")
}
