package com.example.unifiedprefs

import android.app.Application
import android.util.Log

class PocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Synchronous read + write before any UI exists: the case the migration must not break.
        val prefs = LegacyPrefs(this)
        prefs.launchCount = prefs.launchCount + 1
        Log.i("UnifiedPrefs", "UNIFIEDPREFS_STATE dark=${prefs.darkMode} name=${prefs.userName} count=${prefs.launchCount}")
    }
}
