package com.example.unifiedprefs.storage

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Constructors are internal so a key can only be declared in this module.

class BoolKey internal constructor(val name: String, val default: Boolean) {
    internal val key: Preferences.Key<Boolean> = booleanPreferencesKey(name)
}

class IntKey internal constructor(val name: String, val default: Int) {
    internal val key: Preferences.Key<Int> = intPreferencesKey(name)
}

class StringKey internal constructor(val name: String, val default: String) {
    internal val key: Preferences.Key<String> = stringPreferencesKey(name)
}

/** The single registry of preference keys for Android, iOS and shared code. */
object PrefKeys {
    /** Dark theme enabled. */
    val darkMode = BoolKey("dark_mode", false)

    /** Number of process starts. */
    val launchCount = IntKey("launch_count", 0)

    /** Display name entered by the user. */
    val userName = StringKey("user_name", "")

    val allNames: List<String> = listOf(darkMode.name, launchCount.name, userName.name)
}
