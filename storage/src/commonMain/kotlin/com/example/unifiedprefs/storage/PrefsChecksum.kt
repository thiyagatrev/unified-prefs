package com.example.unifiedprefs.storage

private val FNV_OFFSET_BASIS: ULong = 0xcbf29ce484222325uL
private val FNV_PRIME: ULong = 0x100000001b3uL

/** Incremental 64-bit FNV-1a hasher with big-endian integer writes (no ByteBuffer in common code). */
private class Fnv1a64 {
    private var hash: ULong = FNV_OFFSET_BASIS

    fun byte(value: Int) {
        hash = (hash xor (value and 0xFF).toULong()) * FNV_PRIME
    }

    fun bytes(value: ByteArray) {
        for (b in value) byte(b.toInt())
    }

    fun int32(value: Int) {
        for (shift in 24 downTo 0 step 8) byte(value ushr shift)
    }

    fun int64(value: Long) {
        for (shift in 56 downTo 0 step 8) byte((value ushr shift).toInt())
    }

    /** 4-byte big-endian UTF-8 length, then the UTF-8 bytes. */
    fun lengthPrefixedString(value: String) {
        val utf8 = value.encodeToByteArray()
        int32(utf8.size)
        bytes(utf8)
    }

    fun hex(): String = hash.toString(16).padStart(16, '0')
}

/** Contract R4. 64-bit FNV-1a over [keys] sorted by name; 16 lowercase hex chars. */
fun prefsChecksum(keys: List<PrefKey>, valueOf: (PrefKey) -> Any): String {
    val hasher = Fnv1a64()
    for (key in keys.sortedBy { it.name }) {
        hasher.bytes(key.name.encodeToByteArray())
        hasher.byte(0)
        when (key) {
            is BoolKey -> {
                hasher.byte(1)
                hasher.byte(if (valueOf(key) as Boolean) 1 else 0)
            }
            is IntKey -> {
                hasher.byte(2)
                hasher.int32(valueOf(key) as Int)
            }
            is LongKey -> {
                hasher.byte(3)
                hasher.int64(valueOf(key) as Long)
            }
            is FloatKey -> {
                hasher.byte(4)
                hasher.int32((valueOf(key) as Float).toRawBits())
            }
            is DoubleKey -> {
                hasher.byte(5)
                hasher.int64((valueOf(key) as Double).toRawBits())
            }
            is StringKey -> {
                hasher.byte(6)
                hasher.lengthPrefixedString(valueOf(key) as String)
            }
            is StringSetKey -> {
                hasher.byte(7)
                val elements = (valueOf(key) as Set<*>).map { it as String }.sorted()
                hasher.int32(elements.size)
                for (element in elements) {
                    hasher.bytes(element.encodeToByteArray())
                    hasher.byte(0)
                }
            }
        }
    }
    return hasher.hex()
}

fun PrefsRepository.checksum(keys: List<PrefKey> = GeneratedKeys.all): String = prefsChecksum(keys, ::valueOf)
