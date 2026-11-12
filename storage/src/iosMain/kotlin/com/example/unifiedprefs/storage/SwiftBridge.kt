package com.example.unifiedprefs.storage

// Contract C5. Stubs: implemented by WP-C.

fun interface Cancellable {
    fun cancel()
}

fun PrefsRepository.watchBoolean(key: BoolKey, onChange: (Boolean) -> Unit): Cancellable = TODO("WP-C")

fun PrefsRepository.watchInt(key: IntKey, onChange: (Int) -> Unit): Cancellable = TODO("WP-C")

fun PrefsRepository.watchString(key: StringKey, onChange: (String) -> Unit): Cancellable = TODO("WP-C")
