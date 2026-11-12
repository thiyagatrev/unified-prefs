package com.example.unifiedprefs.storage

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefsRepositoryTest {

    private fun repo(seed: Preferences = preferencesOf()): Pair<FakeDataStore, PrefsRepository> {
        val fake = FakeDataStore(seed)
        return fake to PrefsRepository(fake, defaultStorageScope())
    }

    @Test
    fun returnsDefaultsWhenStoreIsEmpty() = runTest {
        val (_, repo) = repo()
        assertEquals(false, repo.getBoolean(PrefKeys.darkMode))
        assertEquals(0, repo.getInt(PrefKeys.launchCount))
        assertEquals("", repo.getString(PrefKeys.userName))
    }

    @Test
    fun readsSeededValuesWithoutSuspending() = runTest {
        val seed = preferencesOf(
            PrefKeys.launchCount.key to 3,
            PrefKeys.userName.key to "Asha",
        )
        val (_, repo) = repo(seed)
        assertEquals(3, repo.getInt(PrefKeys.launchCount))
        assertEquals("Asha", repo.getString(PrefKeys.userName))
    }

    @Test
    fun putIsVisibleToGetImmediately() = runTest {
        val (_, repo) = repo()
        repo.putInt(PrefKeys.launchCount, 5)
        assertEquals(5, repo.getInt(PrefKeys.launchCount))
    }

    @Test
    fun putReachesDataStoreAfterFlush() = runTest {
        val (fake, repo) = repo()
        repo.putString(PrefKeys.userName, "Asha")
        repo.flush()
        assertEquals("Asha", fake.data.first()[PrefKeys.userName.key])
    }

    @Test
    fun lastOfRapidWritesWins() = runTest {
        val (fake, repo) = repo()
        for (i in 1..100) repo.putInt(PrefKeys.launchCount, i)
        repo.flush()
        assertEquals(100, fake.data.first()[PrefKeys.launchCount.key])
    }

    @Test
    fun observeEmitsCurrentThenChangesAndSkipsDuplicates() = runTest {
        val (_, repo) = repo()
        repo.observeBoolean(PrefKeys.darkMode).test {
            assertEquals(false, awaitItem())
            repo.putBoolean(PrefKeys.darkMode, true)
            assertEquals(true, awaitItem())
            repo.putBoolean(PrefKeys.darkMode, true)
            expectNoEvents()
            cancel()
        }
    }
}
