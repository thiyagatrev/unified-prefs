import SwiftUI

@main
struct PocApp: App {
    init() {
        // Without this the state line stays in the stdout buffer and scripts/ios-launch.sh never sees it.
        setvbuf(stdout, nil, _IOLBF, 0)
        if ProcessInfo.processInfo.arguments.contains("--seed") {
            LegacyPrefs.shared.isDarkMode = true
            LegacyPrefs.shared.userName = "Asha"
        }
        LegacyPrefs.shared.launchCount += 1
        print("UNIFIEDPREFS_STATE dark=\(LegacyPrefs.shared.isDarkMode) name=\(LegacyPrefs.shared.userName) count=\(LegacyPrefs.shared.launchCount)")
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
