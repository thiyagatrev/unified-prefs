package com.example.unifiedprefs.storage

/** Contract R4. 64-bit FNV-1a over [keys] sorted by name; 16 lowercase hex chars. */
fun prefsChecksum(keys: List<PrefKey>, valueOf: (PrefKey) -> Any): String = TODO("WP-3")

fun PrefsRepository.checksum(keys: List<PrefKey> = GeneratedKeys.all): String = prefsChecksum(keys, ::valueOf)
