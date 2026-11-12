package com.example.unifiedprefs.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Contract C3. */
class PrefsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    // Blocks once here (S1). After this, every getter reads memory only.
    private val cache = MutableStateFlow(runBlocking { dataStore.data.first() })

    // Number of puts not yet saved to DataStore. flush() waits for zero.
    private val pending = MutableStateFlow(0)

    private fun write(block: (MutablePreferences) -> Unit) {
        cache.update { it.toMutablePreferences().apply(block).toPreferences() }
        pending.update { it + 1 }
        scope.launch {
            try {
                dataStore.edit { block(it) }
            } finally {
                pending.update { it - 1 }
            }
        }
    }

    fun getBoolean(key: BoolKey): Boolean = cache.value[key.key] ?: key.default
    fun putBoolean(key: BoolKey, value: Boolean) = write { it[key.key] = value }
    fun getInt(key: IntKey): Int = cache.value[key.key] ?: key.default
    fun putInt(key: IntKey, value: Int) = write { it[key.key] = value }
    fun getString(key: StringKey): String = cache.value[key.key] ?: key.default
    fun putString(key: StringKey, value: String) = write { it[key.key] = value }

    fun observeBoolean(key: BoolKey): Flow<Boolean> =
        cache.map { it[key.key] ?: key.default }.distinctUntilChanged()

    fun observeInt(key: IntKey): Flow<Int> =
        cache.map { it[key.key] ?: key.default }.distinctUntilChanged()

    fun observeString(key: StringKey): Flow<String> =
        cache.map { it[key.key] ?: key.default }.distinctUntilChanged()

    suspend fun flush() {
        pending.first { it == 0 }
    }
}
