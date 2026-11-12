import Foundation
import Shared

// Native prefs facade over the shared Kotlin storage. Public surface unchanged; storage is IosStorage.
final class LegacyPrefs {
    static let shared = LegacyPrefs()

    private var repo: PrefsRepository { IosStorage.shared.repo }

    private init() {}

    var isDarkMode: Bool {
        get { repo.getBoolean(key: PrefKeys.shared.darkMode) }
        set { repo.putBoolean(key: PrefKeys.shared.darkMode, value: newValue) }
    }

    // Storage holds a 32-bit Int; clamp instead of trapping on a larger value.
    var launchCount: Int {
        get { Int(repo.getInt(key: PrefKeys.shared.launchCount)) }
        set { repo.putInt(key: PrefKeys.shared.launchCount, value: Int32(clamping: newValue)) }
    }

    var userName: String {
        get { repo.getString(key: PrefKeys.shared.userName) }
        set { repo.putString(key: PrefKeys.shared.userName, value: newValue) }
    }
}
