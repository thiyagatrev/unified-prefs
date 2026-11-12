import SwiftUI
import Shared

@main
struct PocApp: App {
    init() {
        // Without this the state line stays in the stdout buffer and scripts/ios-launch.sh never sees it.
        setvbuf(stdout, nil, _IOLBF, 0)
        // Benchmark launch modes (contract R6). Runs before any other storage access, then falls through to the normal start.
        let arguments = ProcessInfo.processInfo.arguments
        if let flag = arguments.firstIndex(of: "--bench"), flag + 1 < arguments.count {
            switch arguments[flag + 1] {
            case "datastore":
                let keysStart = CFAbsoluteTimeGetCurrent()
                _ = PrefKeys.shared.allKeys.count
                let keysMs = (CFAbsoluteTimeGetCurrent() - keysStart) * 1000
                let loadStart = CFAbsoluteTimeGetCurrent()
                let repo = IosStorage.shared.repo
                let loadMs = (CFAbsoluteTimeGetCurrent() - loadStart) * 1000
                let result = PrefsBench.shared.measure(repo: repo)
                print(PrefsBench.shared.datastoreLine(loadMs: loadMs, keysMs: keysMs, r: result))
            case "baseline":
                let defaults = UserDefaults(suiteName: "baseline")!
                let loadStart = CFAbsoluteTimeGetCurrent()
                _ = defaults.dictionaryRepresentation()
                let loadMs = (CFAbsoluteTimeGetCurrent() - loadStart) * 1000
                let next = defaults.integer(forKey: "bench_int") + 1
                let saveStart = CFAbsoluteTimeGetCurrent()
                defaults.set(next, forKey: "bench_int")
                defaults.synchronize()
                let saveMs = (CFAbsoluteTimeGetCurrent() - saveStart) * 1000
                print(PrefsBench.shared.baselineLine(loadMs: loadMs, saveMs: saveMs))
            default:
                break
            }
        }
        let start = CFAbsoluteTimeGetCurrent()
        _ = LegacyPrefs.shared.launchCount // first access: creates the DataStore, runs the migration, loads the file
        print(String(format: "UNIFIEDPREFS_LOAD ms=%.1f", (CFAbsoluteTimeGetCurrent() - start) * 1000))
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
