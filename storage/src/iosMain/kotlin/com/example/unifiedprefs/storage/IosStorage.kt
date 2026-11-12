package com.example.unifiedprefs.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Contract C5. One repository per process, backed by one DataStore. Swift: IosStorage.shared.repo */
object IosStorage {
    val repo: PrefsRepository by lazy {
        PrefsRepository(
            createDataStore(listOf(UserDefaultsMigration()), ::iosStorePath),
            defaultStorageScope(),
        )
    }
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
