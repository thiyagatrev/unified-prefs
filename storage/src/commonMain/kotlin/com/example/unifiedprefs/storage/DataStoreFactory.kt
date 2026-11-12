package com.example.unifiedprefs.storage

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

fun createDataStore(
    migrations: List<DataMigration<Preferences>>,
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    // An unreadable file would otherwise throw on every launch; start from empty instead.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    migrations = migrations,
    produceFile = { producePath().toPath() },
)

// One thread, so background saves run in the order the writes were made.
internal fun defaultStorageScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
