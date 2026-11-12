package com.example.unifiedprefs.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import java.io.File

// Contract C4.

private const val LEGACY_PREFS_NAME = "app_prefs"
private const val DEFAULT_FILE_NAME = "app.preferences_pb"

private val repositoryLock = Any()

@Volatile
private var repository: PrefsRepository? = null

fun prefsRepository(context: Context): PrefsRepository {
    repository?.let { return it }
    synchronized(repositoryLock) {
        repository?.let { return it }
        val created = PrefsRepository(
            androidDataStore(context.applicationContext),
            defaultStorageScope(),
        )
        repository = created
        return created
    }
}

internal fun androidDataStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
): DataStore<Preferences> = createDataStore(
    migrations = listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME)),
    producePath = { File(context.filesDir, "datastore/$fileName").absolutePath },
)

internal fun resetStorageForTest() {
    synchronized(repositoryLock) {
        repository = null
    }
}
