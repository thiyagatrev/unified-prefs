import SwiftUI
import Shared

// Wave 0 placeholder: proves the Kotlin framework links and the launch script can read stdout.
@main
struct PocApp: App {
    init() {
        setvbuf(stdout, nil, _IOLBF, 0)
        print("UNIFIEDPREFS_STATE dark=false name= count=0 keys=\(PrefKeys.shared.allNames.count)")
    }

    var body: some Scene {
        WindowGroup { Text("placeholder") }
    }
}
