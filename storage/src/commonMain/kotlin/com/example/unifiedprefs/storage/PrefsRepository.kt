package com.example.unifiedprefs.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private class Snapshot(val prefs: Preferences, val version: Long)

    // Blocks once here (S1). After this, every getter reads memory only.
    private val state = MutableStateFlow(Snapshot(runBlocking { dataStore.data.first() }, 0))
    private val savedVersion = MutableStateFlow(0L)
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val failures = MutableStateFlow(0)
    private val saves = MutableStateFlow(0)

    /** Number of background saves that failed. Memory keeps the value; the next save retries it. */
    val saveFailures: StateFlow<Int> = failures

    init {
        // The saver writes the latest snapshot as a whole, so disk can never end up behind memory
        // because two writers queued in a different order than they updated memory.
        scope.launch {
            for (signal in saveSignal) {
                val snapshot = state.value
                try {
                    dataStore.updateData { snapshot.prefs }
                    saves.update { it + 1 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.update { it + 1 }
                }
                savedVersion.value = snapshot.version
            }
        }
    }

    private fun write(block: (MutablePreferences) -> Unit) {
        state.update { Snapshot(it.prefs.toMutablePreferences().apply(block).toPreferences(), it.version + 1) }
        saveSignal.trySend(Unit)
    }

    fun getBoolean(key: BoolKey): Boolean = state.value.prefs[key.key] ?: key.default
    fun putBoolean(key: BoolKey, value: Boolean) = write { it[key.key] = value }

    fun getInt(key: IntKey): Int = state.value.prefs[key.key] ?: key.default
    fun putInt(key: IntKey, value: Int) = write { it[key.key] = value }

    fun getString(key: StringKey): String = state.value.prefs[key.key] ?: key.default
    fun putString(key: StringKey, value: String) = write { it[key.key] = value }

    fun getLong(key: LongKey): Long = state.value.prefs[key.key] ?: key.default
    fun putLong(key: LongKey, value: Long) = write { it[key.key] = value }
    fun observeLong(key: LongKey): Flow<Long> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    fun getFloat(key: FloatKey): Float = state.value.prefs[key.key] ?: key.default
    fun putFloat(key: FloatKey, value: Float) = write { it[key.key] = value }
    fun observeFloat(key: FloatKey): Flow<Float> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    fun getDouble(key: DoubleKey): Double = state.value.prefs[key.key] ?: key.default
    fun putDouble(key: DoubleKey, value: Double) = write { it[key.key] = value }
    fun observeDouble(key: DoubleKey): Flow<Double> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    fun getStringSet(key: StringSetKey): Set<String> = state.value.prefs[key.key] ?: key.default
    fun putStringSet(key: StringSetKey, value: Set<String>) = write { it[key.key] = value }
    fun observeStringSet(key: StringSetKey): Flow<Set<String>> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    /** The current value of any key, boxed; the key's default if unset. */
    fun valueOf(key: PrefKey): Any {
        val prefs = state.value.prefs
        return when (key) {
            is BoolKey -> prefs[key.key] ?: key.default
            is IntKey -> prefs[key.key] ?: key.default
            is LongKey -> prefs[key.key] ?: key.default
            is FloatKey -> prefs[key.key] ?: key.default
            is DoubleKey -> prefs[key.key] ?: key.default
            is StringKey -> prefs[key.key] ?: key.default
            is StringSetKey -> prefs[key.key] ?: key.default
        }
    }

    /** Saves completed successfully since this repository was created. */
    val saveCount: StateFlow<Int> = saves

    fun observeBoolean(key: BoolKey): Flow<Boolean> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    fun observeInt(key: IntKey): Flow<Int> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    fun observeString(key: StringKey): Flow<String> =
        state.map { it.prefs[key.key] ?: key.default }.distinctUntilChanged()

    /** Returns once every put made before this call has been attempted; see [saveFailures]. */
    suspend fun flush() {
        val target = state.value.version
        savedVersion.first { it >= target }
    }
}
