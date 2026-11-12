package com.example.unifiedprefs.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Contract C5. Stub: implemented by WP-C. */
object IosStorage {
    val repo: PrefsRepository get() = TODO("WP-C")
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosStorePath(): String {
    val dir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(dir?.path) { "Application Support directory is unavailable" } + "/app.preferences_pb"
}
