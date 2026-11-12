package com.example.unifiedprefs.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory DataStore for tests. */
class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    /** Number of upcoming writes that should fail, to simulate a full disk. */
    var failNextWrites: Int = 0

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            if (failNextWrites > 0) {
                failNextWrites--
                throw IllegalStateException("simulated write failure")
            }
            transform(state.value).also { state.value = it }
        }
}
