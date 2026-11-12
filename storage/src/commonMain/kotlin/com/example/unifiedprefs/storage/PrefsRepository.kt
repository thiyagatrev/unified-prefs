package com.example.unifiedprefs.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** Contract C3. Stub: implemented by WP-A. */
class PrefsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    fun getBoolean(key: BoolKey): Boolean = TODO("WP-A")
    fun putBoolean(key: BoolKey, value: Boolean): Unit = TODO("WP-A")
    fun getInt(key: IntKey): Int = TODO("WP-A")
    fun putInt(key: IntKey, value: Int): Unit = TODO("WP-A")
    fun getString(key: StringKey): String = TODO("WP-A")
    fun putString(key: StringKey, value: String): Unit = TODO("WP-A")
    fun observeBoolean(key: BoolKey): Flow<Boolean> = TODO("WP-A")
    fun observeInt(key: IntKey): Flow<Int> = TODO("WP-A")
    fun observeString(key: StringKey): Flow<String> = TODO("WP-A")
    suspend fun flush(): Unit = TODO("WP-A")
}
