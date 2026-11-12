package com.example.unifiedprefs.storage

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PrefsChecksumTest {

    private val vectorKeys: List<PrefKey> = listOf(
        BoolKey("tv_bool", false),
        DoubleKey("tv_double", 0.0),
        FloatKey("tv_float", 0f),
        IntKey("tv_int", 0),
        LongKey("tv_long", 0L),
        StringSetKey("tv_set", emptySet()),
        StringKey("tv_str", ""),
        StringKey("tv_unset", "dflt"),
    )

    private val vectorValues: Map<String, Any> = mapOf(
        "tv_bool" to true,
        "tv_double" to -0.1,
        "tv_float" to 1.5f,
        "tv_int" to -2147483648,
        "tv_long" to 1731400000000L,
        "tv_set" to setOf("b", "a", "c"),
        "tv_str" to "நன்றி 🙏 <a&b>",
        // "tv_unset" has no entry, so valueOf falls back to its default "dflt".
    )

    private fun lookup(values: Map<String, Any>): (PrefKey) -> Any = { key ->
        values[key.name] ?: defaultOf(key)
    }

    private fun defaultOf(key: PrefKey): Any = when (key) {
        is BoolKey -> key.default
        is IntKey -> key.default
        is LongKey -> key.default
        is FloatKey -> key.default
        is DoubleKey -> key.default
        is StringKey -> key.default
        is StringSetKey -> key.default
    }

    @Test
    fun emptyListGivesOffsetBasis() {
        assertEquals("cbf29ce484222325", prefsChecksum(emptyList()) { error("no keys") })
    }

    @Test
    fun matchesPythonTestVector() {
        assertEquals(
            "226e901c9354c740",
            prefsChecksum(vectorKeys, lookup(vectorValues)),
        )
    }

    @Test
    fun orderOfInputDoesNotMatter() {
        assertEquals(
            prefsChecksum(vectorKeys, lookup(vectorValues)),
            prefsChecksum(vectorKeys.shuffled(Random(7)), lookup(vectorValues)),
        )
    }

    @Test
    fun changingOneValueChangesChecksum() {
        val changed = vectorValues + ("tv_float" to 1.5000001f)
        assertNotEquals(
            prefsChecksum(vectorKeys, lookup(vectorValues)),
            prefsChecksum(vectorKeys, lookup(changed)),
        )
    }

    @Test
    fun setOrderDoesNotMatter() {
        val abc = vectorValues + ("tv_set" to setOf("a", "b", "c"))
        val cab = vectorValues + ("tv_set" to setOf("c", "a", "b"))
        assertEquals(
            prefsChecksum(vectorKeys, lookup(abc)),
            prefsChecksum(vectorKeys, lookup(cab)),
        )
    }
}
