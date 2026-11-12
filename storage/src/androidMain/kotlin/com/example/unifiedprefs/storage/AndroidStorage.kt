package com.example.unifiedprefs.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

// Contract C4. Stubs: implemented by WP-B.

fun prefsRepository(context: Context): PrefsRepository = TODO("WP-B")

internal fun androidDataStore(
    context: Context,
    fileName: String = "app.preferences_pb",
): DataStore<Preferences> = TODO("WP-B")

internal fun resetStorageForTest(): Unit = TODO("WP-B")
