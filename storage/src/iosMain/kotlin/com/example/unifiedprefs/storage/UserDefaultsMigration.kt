package com.example.unifiedprefs.storage

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import platform.Foundation.NSUserDefaults

/**
 * Contract C5. Copies the iOS legacy NSUserDefaults values into the DataStore once.
 * Walks every key in [keys] and reads each by its [PrefKey.legacyIosName], typed by the registry.
 */
class UserDefaultsMigration(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val keys: List<PrefKey> = PrefKeys.allKeys,
) : DataMigration<Preferences> {

    private val marker = booleanPreferencesKey("__ud_migrated")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[marker] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = currentData.toMutablePreferences()
        for (key in keys) {
            val legacy = key.legacyIosName
            if (defaults.objectForKey(legacy) == null) continue
            when (key) {
                is BoolKey -> out[key.key] = defaults.boolForKey(legacy)
                is IntKey -> out[key.key] = defaults.integerForKey(legacy)
                    .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    .toInt()
                is LongKey -> out[key.key] = defaults.integerForKey(legacy)
                is FloatKey -> out[key.key] = defaults.floatForKey(legacy)
                is DoubleKey -> out[key.key] = defaults.doubleForKey(legacy)
                is StringKey -> defaults.stringForKey(legacy)?.let { out[key.key] = it }
                is StringSetKey -> defaults.arrayForKey(legacy)
                    ?.filterIsInstance<String>()
                    ?.toSet()
                    ?.let { out[key.key] = it }
            }
        }
        out[marker] = true
        return out.toPreferences()
    }

    // Legacy NSUserDefaults values are left in place, so there is nothing to clean up.
    override suspend fun cleanUp() {}
}
