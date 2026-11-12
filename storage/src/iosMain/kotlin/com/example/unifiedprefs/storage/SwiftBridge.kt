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
