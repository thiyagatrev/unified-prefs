package com.example.unifiedprefs.storage

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import platform.Foundation.NSUserDefaults

/** Contract C5. Stub: implemented by WP-C. */
class UserDefaultsMigration(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = TODO("WP-C")
    override suspend fun migrate(currentData: Preferences): Preferences = TODO("WP-C")
    override suspend fun cleanUp(): Unit = TODO("WP-C")
}
