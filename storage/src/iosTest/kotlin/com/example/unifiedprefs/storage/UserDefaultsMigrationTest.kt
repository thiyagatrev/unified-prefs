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

    @Test
    fun migratesEveryType() = runTest {
        withSuite("migratesEveryType") { d ->
            val bool = BoolKey("t_bool", false)
            val int = IntKey("t_int", 0)
            val long = LongKey("t_long", 0L)
            val float = FloatKey("t_float", 0f)
            val double = DoubleKey("t_double", 0.0)
            val str = StringKey("t_str", "")
            val set = StringSetKey("t_set", emptySet())
            d.setBool(true, forKey = bool.legacyIosName)
            d.setInteger(42, forKey = int.legacyIosName)
            d.setInteger(9_000_000_000, forKey = long.legacyIosName)
            d.setFloat(1.5f, forKey = float.legacyIosName)
            d.setDouble(-0.1, forKey = double.legacyIosName)
            d.setObject("hi", forKey = str.legacyIosName)
            d.setObject(listOf("a", "b"), forKey = set.legacyIosName)

            val out = UserDefaultsMigration(d, listOf(bool, int, long, float, double, str, set))
                .migrate(emptyPreferences())

            assertEquals(true, out[bool.key])
            assertEquals(42, out[int.key])
            assertEquals(9_000_000_000L, out[long.key])
            assertEquals(1.5f, out[float.key])
            assertEquals(-0.1, out[double.key])
            assertEquals("hi", out[str.key])
            assertEquals(setOf("a", "b"), out[set.key])
        }
    }

    @Test
    fun readsNumberByRegistryType() = runTest {
        withSuite("readsNumberByRegistryType") { d ->
            val int = IntKey("t_int_real", 0)
            val bool = BoolKey("t_bool_int", false)
            d.setDouble(2.7, forKey = int.legacyIosName)
            d.setInteger(1, forKey = bool.legacyIosName)

            val out = UserDefaultsMigration(d, listOf(int, bool)).migrate(emptyPreferences())

            assertEquals(2, out[int.key])
            assertEquals(true, out[bool.key])
        }
    }

    @Test
    fun migratesUnicodeAndEscapes() = runTest {
        withSuite("migratesUnicodeAndEscapes") { d ->
            val text = StringKey("t_text", "")
            val value = "நன்றி 🙏 <a&b>\n\"q\""
            d.setObject(value, forKey = text.legacyIosName)

            val out = UserDefaultsMigration(d, listOf(text)).migrate(emptyPreferences())

            assertEquals(value, out[text.key])
        }
    }

    @Test
    fun setIgnoresNonStringElements() = runTest {
        withSuite("setIgnoresNonStringElements") { d ->
            val set = StringSetKey("t_set2", emptySet())
            d.setObject(listOf("a", 1, "b"), forKey = set.legacyIosName)

            val out = UserDefaultsMigration(d, listOf(set)).migrate(emptyPreferences())

            assertEquals(setOf("a", "b"), out[set.key])
        }
    }

    @Test
    fun usesLegacyIosName() = runTest {
        withSuite("usesLegacyIosName") { d ->
            val name = StringKey("user_name", "", legacyIosName = "userName")
            d.setObject("Asha", forKey = "userName")

            val out = UserDefaultsMigration(d, listOf(name)).migrate(emptyPreferences())

            assertEquals("Asha", out[name.key])
        }
    }
}
