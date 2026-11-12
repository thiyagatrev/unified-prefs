package com.example.unifiedprefs.storage

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun concurrentWritersLeaveDiskEqualToMemory() = runTest {
        val (fake, repo) = repo()
        repeat(20) { round ->
            (1..8).map { writer ->
                launch(Dispatchers.Default) {
                    repeat(200) { repo.putInt(PrefKeys.launchCount, round * 10_000 + writer * 1_000 + it) }
                }
            }.joinAll()
            repo.flush()
            assertEquals(repo.getInt(PrefKeys.launchCount), fake.data.first()[PrefKeys.launchCount.key], "round $round")
        }
    }

    @Test
    fun failedSaveIsReportedAndLaterSavesStillWork() = runTest {
        val (fake, repo) = repo()
        fake.failNextWrites = 1
        repo.putInt(PrefKeys.launchCount, 5)
        repo.flush()
        assertEquals(1, repo.saveFailures.value)
        assertEquals(5, repo.getInt(PrefKeys.launchCount))

        repo.putString(PrefKeys.userName, "Asha")
        repo.flush()
        val saved = fake.data.first()
        assertEquals("Asha", saved[PrefKeys.userName.key])
        assertEquals(5, saved[PrefKeys.launchCount.key]) // the earlier value reaches disk with the next save
    }

    @Test
    fun changeToAnotherKeyDoesNotReEmit() = runTest {
        val (_, repo) = repo()
        repo.observeBoolean(PrefKeys.darkMode).test {
            assertEquals(false, awaitItem())
            repo.putString(PrefKeys.userName, "Asha")
            expectNoEvents()
            cancel()
        }
    }

    private val longKey = LongKey("t_long", 0L)
    private val floatKey = FloatKey("t_float", 0f)
    private val doubleKey = DoubleKey("t_double", 0.0)
    private val setKey = StringSetKey("t_set", emptySet())

    @Test
    fun newTypesReturnDefaultsWhenEmpty() = runTest {
        val (_, repo) = repo()
        assertEquals(0L, repo.getLong(longKey))
        assertEquals(0f, repo.getFloat(floatKey))
        assertEquals(0.0, repo.getDouble(doubleKey))
        assertEquals(emptySet(), repo.getStringSet(setKey))
    }

    @Test
    fun newTypesPutVisibleImmediately() = runTest {
        val (_, repo) = repo()
        repo.putLong(longKey, Long.MAX_VALUE)
        repo.putFloat(floatKey, 1.5f)
        repo.putDouble(doubleKey, -0.1)
        repo.putStringSet(setKey, setOf("a", "b"))
        assertEquals(Long.MAX_VALUE, repo.getLong(longKey))
        assertEquals(1.5f, repo.getFloat(floatKey))
        assertEquals(-0.1, repo.getDouble(doubleKey))
        assertEquals(setOf("a", "b"), repo.getStringSet(setKey))
    }

    @Test
    fun newTypesReachDiskAfterFlush() = runTest {
        val (fake, repo) = repo()
        repo.putLong(longKey, Long.MAX_VALUE)
        repo.putFloat(floatKey, 1.5f)
        repo.putDouble(doubleKey, -0.1)
        repo.putStringSet(setKey, setOf("a", "b"))
        repo.flush()
        val saved = fake.data.first()
        assertEquals(Long.MAX_VALUE, saved[longKey.key])
        assertEquals(1.5f, saved[floatKey.key])
        assertEquals(-0.1, saved[doubleKey.key])
        assertEquals(setOf("a", "b"), saved[setKey.key])
    }

    @Test
    fun observeStringSetEmitsOnChangeOnly() = runTest {
        val (_, repo) = repo()
        repo.observeStringSet(setKey).test {
            assertEquals(emptySet(), awaitItem())
            repo.putStringSet(setKey, setOf("a"))
            assertEquals(setOf("a"), awaitItem())
            repo.putStringSet(setKey, setOf("a"))
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun valueOfBoxesEachType() = runTest {
        val (_, repo) = repo()
        val bool = BoolKey("t_bool", false)
        val int = IntKey("t_int", 0)
        val str = StringKey("t_str", "dflt")
        repo.putBoolean(bool, true)
        repo.putInt(int, 7)
        repo.putString(str, "x")
        repo.putLong(longKey, 9L)
        repo.putFloat(floatKey, 2.5f)
        repo.putDouble(doubleKey, 3.25)
        repo.putStringSet(setKey, setOf("q"))
        assertEquals(true, repo.valueOf(bool))
        assertEquals(7, repo.valueOf(int))
        assertEquals("x", repo.valueOf(str))
        assertEquals(9L, repo.valueOf(longKey))
        assertEquals(2.5f, repo.valueOf(floatKey))
        assertEquals(3.25, repo.valueOf(doubleKey))
        assertEquals(setOf("q"), repo.valueOf(setKey))
    }

    @Test
    fun valueOfReturnsDefaultForUnsetKey() = runTest {
        val (_, repo) = repo()
        assertEquals("dflt", repo.valueOf(StringKey("t_unset", "dflt")))
        assertEquals(0L, repo.valueOf(longKey))
    }

    @Test
    fun saveCountCountsSuccessfulSaves() = runTest {
        val (fake, repo) = repo()
        assertEquals(0, repo.saveCount.value)
        repo.putInt(PrefKeys.launchCount, 1)
        repo.flush()
        assertEquals(1, repo.saveCount.value)

        fake.failNextWrites = 1
        repo.putInt(PrefKeys.launchCount, 2)
        repo.flush()
        assertEquals(1, repo.saveCount.value)
    }

    @Test
    fun manyPutsCoalesceIntoFewSaves() = runTest {
        val (fake, repo) = repo()
        fake.gate.lock()
        for (i in 1..100) repo.putInt(PrefKeys.launchCount, i)
        fake.gate.unlock()
        repo.flush()
        assertTrue(repo.saveCount.value in 1..2, "saves: ${repo.saveCount.value}")
        assertEquals(100, fake.data.first()[PrefKeys.launchCount.key])
    }
}
