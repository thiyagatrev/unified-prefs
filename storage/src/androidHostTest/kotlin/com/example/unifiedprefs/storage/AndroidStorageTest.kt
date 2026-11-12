package com.example.unifiedprefs.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun seed() {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode", true)
            .putInt("launch_count", 3)
            .putString("user_name", "Asha")
            .commit()
    }

    @Test
    fun migratesExistingSharedPreferences() = runBlocking {
        seed()
        val p = androidDataStore(context, "t1.preferences_pb").data.first()
        assertEquals(true, p[PrefKeys.darkMode.key])
        assertEquals(3, p[PrefKeys.launchCount.key])
        assertEquals("Asha", p[PrefKeys.userName.key])
    }

    @Test
    fun migratedKeysAreRemovedFromSharedPreferences() = runBlocking {
        seed()
        androidDataStore(context, "t2.preferences_pb").data.first()
        assertTrue(
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).all.isEmpty()
        )
    }

    @Test
    fun emptySharedPreferencesYieldsNoStoredKeys() = runBlocking {
        val p = androidDataStore(context, "t3.preferences_pb").data.first()
        assertTrue(p.asMap().isEmpty())
    }

    @Test
    fun storesUnderFilesDirDatastore() = runBlocking {
        androidDataStore(context, "t4.preferences_pb").edit {
            it[PrefKeys.launchCount.key] = 1
        }
        assertTrue(File(context.filesDir, "datastore/t4.preferences_pb").exists())
    }

    @Test
    fun sharedPreferencesMigrationKeepsLongFloatAndSet() = runBlocking {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("big_long", Long.MAX_VALUE)
            .putFloat("some_float", 0.1f)
            .putStringSet("some_set", setOf("x", "y"))
            .commit()
        val p = androidDataStore(context, "t5.preferences_pb").data.first()
        assertEquals(Long.MAX_VALUE, p[androidx.datastore.preferences.core.longPreferencesKey("big_long")])
        assertEquals(0.1f, p[androidx.datastore.preferences.core.floatPreferencesKey("some_float")])
        assertEquals(setOf("x", "y"), p[androidx.datastore.preferences.core.stringSetPreferencesKey("some_set")])
    }

    @Test
    fun accessorReturnsSameInstanceOnEveryCall() {
        resetStorageForTest()
        assertSame(prefsRepository(context), prefsRepository(context))
    }

    @Test
    fun resetClosesTheStoreSoTheNextRepositoryCanOpenTheSameFile() = runBlocking {
        resetStorageForTest()
        val first = prefsRepository(context)
        first.putString(PrefKeys.userName, "Bo")
        first.flush()
        resetStorageForTest()
        assertEquals("Bo", prefsRepository(context).getString(PrefKeys.userName))
        resetStorageForTest()
    }
}
