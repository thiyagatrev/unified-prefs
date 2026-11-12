package com.example.unifiedprefs.storage

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDefaultsMigrationTest {

    // Each test gets its own suite, cleared before and after the test body.
    private suspend fun withSuite(name: String, block: suspend (NSUserDefaults) -> Unit) {
        val suite = "test-$name"
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.removePersistentDomainForName(suite)
        try {
            block(defaults)
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }

    @Test
    fun mapsIosLegacyKeysToStoredNames() = runTest {
        withSuite("mapsIosLegacyKeysToStoredNames") { d ->
            d.setBool(true, forKey = "isDarkMode")
            d.setInteger(3, forKey = "launchCount")
            d.setObject("Asha", forKey = "userName")

            val out = UserDefaultsMigration(d).migrate(emptyPreferences())

            assertEquals(true, out[PrefKeys.darkMode.key])
            assertEquals(3, out[PrefKeys.launchCount.key])
            assertEquals("Asha", out[PrefKeys.userName.key])
        }
    }

    @Test
    fun skipsAbsentKeys() = runTest {
        withSuite("skipsAbsentKeys") { d ->
            d.setObject("Asha", forKey = "userName")

            val out = UserDefaultsMigration(d).migrate(emptyPreferences())

            assertNull(out[PrefKeys.darkMode.key])
            assertNull(out[PrefKeys.launchCount.key])
        }
    }

    @Test
    fun clampsIntegerOutsideIntRange() = runTest {
        withSuite("clampsIntegerOutsideIntRange") { d ->
            d.setInteger(5_000_000_000, forKey = "launchCount")

            val out = UserDefaultsMigration(d).migrate(emptyPreferences())

            assertEquals(Int.MAX_VALUE, out[PrefKeys.launchCount.key])
        }
    }

    @Test
    fun setsMarkerAndStopsMigrating() = runTest {
        withSuite("setsMarkerAndStopsMigrating") { d ->
            val m = UserDefaultsMigration(d)

            assertTrue(m.shouldMigrate(emptyPreferences()))
            assertFalse(m.shouldMigrate(m.migrate(emptyPreferences())))
        }
    }

    @Test
    fun leavesLegacyValuesInPlace() = runTest {
        withSuite("leavesLegacyValuesInPlace") { d ->
            d.setObject("Asha", forKey = "userName")
            val m = UserDefaultsMigration(d)

            m.migrate(emptyPreferences())
            m.cleanUp()

            assertEquals("Asha", d.stringForKey("userName"))
        }
    }
}
