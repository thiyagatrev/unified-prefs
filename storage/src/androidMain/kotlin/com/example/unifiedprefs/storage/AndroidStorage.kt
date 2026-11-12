package com.example.unifiedprefs.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File

// Contract C4.

private const val LEGACY_PREFS_NAME = "app_prefs"
private const val DEFAULT_FILE_NAME = "app.preferences_pb"

private val repositoryLock = Any()

@Volatile
private var repository: PrefsRepository? = null

// Parent of the store's and the saver's coroutines, so a test reset can close both.
private var storageJob = SupervisorJob()

fun prefsRepository(context: Context): PrefsRepository {
    repository?.let { return it }
    synchronized(repositoryLock) {
        repository?.let { return it }
        val job = SupervisorJob().also { storageJob = it }
        val created = PrefsRepository(
            androidDataStore(
                context.applicationContext,
                scope = CoroutineScope(Dispatchers.IO + job),
            ),
            CoroutineScope(defaultStorageScope().coroutineContext + job),
        )
        repository = created
        return created
    }
}

internal fun androidDataStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = createDataStore(
    migrations = listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME)),
    producePath = { File(context.filesDir, "datastore/$fileName").absolutePath },
    scope = scope,
)

internal fun resetStorageForTest() {
    synchronized(repositoryLock) {
        repository = null
        runBlocking { storageJob.cancelAndJoin() }
    }
}
