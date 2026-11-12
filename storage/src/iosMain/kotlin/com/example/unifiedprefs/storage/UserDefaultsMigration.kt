package com.example.unifiedprefs.storage

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import platform.Foundation.NSUserDefaults

/** Contract C5. Copies the iOS legacy NSUserDefaults values into the DataStore once. */
class UserDefaultsMigration(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DataMigration<Preferences> {

    private val marker = booleanPreferencesKey("__ud_migrated")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[marker] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = currentData.toMutablePreferences()
        if (defaults.objectForKey("isDarkMode") != null) {
            out[PrefKeys.darkMode.key] = defaults.boolForKey("isDarkMode")
        }
        if (defaults.objectForKey("launchCount") != null) {
            out[PrefKeys.launchCount.key] = defaults.integerForKey("launchCount")
                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                .toInt()
        }
        defaults.stringForKey("userName")?.let { out[PrefKeys.userName.key] = it }
        out[marker] = true
        return out.toPreferences()
    }

    // Legacy NSUserDefaults values are left in place, so there is nothing to clean up.
    override suspend fun cleanUp() {}
}
