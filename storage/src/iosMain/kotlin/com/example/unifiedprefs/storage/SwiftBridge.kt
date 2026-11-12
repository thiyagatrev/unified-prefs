package com.example.unifiedprefs.storage

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Contract C5. Stops a watch started by a watchX function. */
fun interface Cancellable {
    fun cancel()
}

fun PrefsRepository.watchBoolean(key: BoolKey, onChange: (Boolean) -> Unit): Cancellable {
    val job = MainScope().launch { observeBoolean(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchInt(key: IntKey, onChange: (Int) -> Unit): Cancellable {
    val job = MainScope().launch { observeInt(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchString(key: StringKey, onChange: (String) -> Unit): Cancellable {
    val job = MainScope().launch { observeString(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchLong(key: LongKey, onChange: (Long) -> Unit): Cancellable {
    val job = MainScope().launch { observeLong(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchFloat(key: FloatKey, onChange: (Float) -> Unit): Cancellable {
    val job = MainScope().launch { observeFloat(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchDouble(key: DoubleKey, onChange: (Double) -> Unit): Cancellable {
    val job = MainScope().launch { observeDouble(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}

fun PrefsRepository.watchStringSet(key: StringSetKey, onChange: (Set<String>) -> Unit): Cancellable {
    val job = MainScope().launch { observeStringSet(key).collect { onChange(it) } }
    return Cancellable { job.cancel() }
}
