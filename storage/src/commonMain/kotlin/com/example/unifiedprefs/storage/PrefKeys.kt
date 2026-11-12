package com.example.unifiedprefs.storage

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

// Constructors are internal so a key can only be declared in this module.

/** Any registry key. [legacyIosName] is the NSUserDefaults key the iOS migration reads. */
sealed class PrefKey(val name: String, val legacyIosName: String)

class BoolKey internal constructor(name: String, val default: Boolean, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Boolean> = booleanPreferencesKey(name)
}

class IntKey internal constructor(name: String, val default: Int, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Int> = intPreferencesKey(name)
}

class LongKey internal constructor(name: String, val default: Long, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Long> = longPreferencesKey(name)
}

class FloatKey internal constructor(name: String, val default: Float, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Float> = floatPreferencesKey(name)
}

class DoubleKey internal constructor(name: String, val default: Double, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Double> = doublePreferencesKey(name)
}

class StringKey internal constructor(name: String, val default: String, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<String> = stringPreferencesKey(name)
}

class StringSetKey internal constructor(name: String, val default: Set<String>, legacyIosName: String = name) :
    PrefKey(name, legacyIosName) {
    internal val key: Preferences.Key<Set<String>> = stringSetPreferencesKey(name)
}

/** The single registry of preference keys for Android, iOS and shared code. */
object PrefKeys {
    /** Dark theme enabled. */
    val darkMode = BoolKey("dark_mode", false, legacyIosName = "isDarkMode")

    /** Number of process starts. */
    val launchCount = IntKey("launch_count", 0, legacyIosName = "launchCount")

    /** Display name entered by the user. */
    val userName = StringKey("user_name", "", legacyIosName = "userName")

    /** Written only by the benchmark, so it never changes a generated key. */
    val benchInt = IntKey("bench_int", 0)

    /** Written only by the benchmark. */
    val benchText = StringKey("bench_text", "")

    val allKeys: List<PrefKey> =
        listOf(darkMode, launchCount, userName, benchInt, benchText) + GeneratedKeys.all

    val allNames: List<String> = allKeys.map { it.name }
}
