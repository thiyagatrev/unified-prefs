package com.example.unifiedprefs

import android.app.Application
import android.util.Log

class PocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Synchronous read + write before any UI exists: the case the migration must not break.
        val start = System.nanoTime()
        val prefs = LegacyPrefs(this) // first access: creates the DataStore, runs the migration, loads the file
        Log.i("UnifiedPrefs", "UNIFIEDPREFS_LOAD ms=${(System.nanoTime() - start) / 1_000_000.0}")
        prefs.launchCount = prefs.launchCount + 1
        Log.i("UnifiedPrefs", "UNIFIEDPREFS_STATE dark=${prefs.darkMode} name=${prefs.userName} count=${prefs.launchCount}")
    }
}
