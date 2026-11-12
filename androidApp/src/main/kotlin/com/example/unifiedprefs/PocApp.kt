package com.example.unifiedprefs

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.unifiedprefs.storage.PrefKeys
import com.example.unifiedprefs.storage.PrefsBench
import com.example.unifiedprefs.storage.prefsRepository
import java.io.File
import kotlin.time.TimeSource

class PocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bench mode runs before any other storage access in the process.
        val mode = File(filesDir, "bench_mode").takeIf { it.exists() }?.readText()?.trim()
        when (mode) {
            "datastore" -> {
                val keysStart = TimeSource.Monotonic.markNow()
                PrefKeys.allKeys.size
                val keysMs = keysStart.elapsedNow().inWholeNanoseconds / 1_000_000.0
                val loadStart = TimeSource.Monotonic.markNow()
                val repo = prefsRepository(this)
                val loadMs = loadStart.elapsedNow().inWholeNanoseconds / 1_000_000.0
                val result = PrefsBench.measure(repo)
                Log.i("UnifiedPrefs", PrefsBench.datastoreLine(loadMs, keysMs, result))
            }
            "baseline" -> {
                val loadStart = TimeSource.Monotonic.markNow()
                val prefs = getSharedPreferences("baseline_prefs", Context.MODE_PRIVATE)
                val all = prefs.all
                val loadMs = loadStart.elapsedNow().inWholeNanoseconds / 1_000_000.0
                val n = (all["bench_int"] as? Int) ?: 0
                val saveStart = TimeSource.Monotonic.markNow()
                prefs.edit().putInt("bench_int", n + 1).commit()
                val saveMs = saveStart.elapsedNow().inWholeNanoseconds / 1_000_000.0
                Log.i("UnifiedPrefs", PrefsBench.baselineLine(loadMs, saveMs))
            }
        }
        // Synchronous read + write before any UI exists: the case the migration must not break.
        val start = System.nanoTime()
        val prefs = LegacyPrefs(this) // first access: creates the DataStore, runs the migration, loads the file
        Log.i("UnifiedPrefs", "UNIFIEDPREFS_LOAD ms=${(System.nanoTime() - start) / 1_000_000.0}")
        prefs.launchCount = prefs.launchCount + 1
        Log.i("UnifiedPrefs", "UNIFIEDPREFS_STATE dark=${prefs.darkMode} name=${prefs.userName} count=${prefs.launchCount}")
    }
}
