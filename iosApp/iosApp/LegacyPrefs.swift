import Foundation

// Before-state storage: plain UserDefaults, the legacy iOS prefs the migration reads from.
final class LegacyPrefs {
    static let shared = LegacyPrefs()

    private let defaults = UserDefaults.standard

    private init() {}

    var isDarkMode: Bool {
        get { defaults.bool(forKey: "isDarkMode") }
        set { defaults.set(newValue, forKey: "isDarkMode") }
    }

    var launchCount: Int {
        get { defaults.integer(forKey: "launchCount") }
        set { defaults.set(newValue, forKey: "launchCount") }
    }

    var userName: String {
        get { defaults.string(forKey: "userName") ?? "" }
        set { defaults.set(newValue, forKey: "userName") }
    }
}
