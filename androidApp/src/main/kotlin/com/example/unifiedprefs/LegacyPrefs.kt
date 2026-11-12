package com.example.unifiedprefs

import android.content.Context

/** The app's existing preferences class. Its public surface must not change during the migration. */
class LegacyPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var launchCount: Int
        get() = prefs.getInt("launch_count", 0)
        set(value) = prefs.edit().putInt("launch_count", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value).apply()
}
