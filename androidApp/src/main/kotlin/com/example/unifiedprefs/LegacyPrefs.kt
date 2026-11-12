package com.example.unifiedprefs

import android.content.Context
import com.example.unifiedprefs.storage.PrefKeys
import com.example.unifiedprefs.storage.prefsRepository

/** The app's existing preferences class. Its public surface must not change during the migration. */
class LegacyPrefs(context: Context) {
    private val repo = prefsRepository(context)

    var darkMode: Boolean
        get() = repo.getBoolean(PrefKeys.darkMode)
        set(value) = repo.putBoolean(PrefKeys.darkMode, value)

    var launchCount: Int
        get() = repo.getInt(PrefKeys.launchCount)
        set(value) = repo.putInt(PrefKeys.launchCount, value)

    var userName: String
        get() = repo.getString(PrefKeys.userName)
        set(value) = repo.putString(PrefKeys.userName, value)
}
