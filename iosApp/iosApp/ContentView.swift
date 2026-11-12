import SwiftUI

struct ContentView: View {
    @State private var isDark: Bool = LegacyPrefs.shared.isDarkMode
    @State private var userName: String = LegacyPrefs.shared.userName
    @State private var count: Int = LegacyPrefs.shared.launchCount

    var body: some View {
        VStack(spacing: 16) {
            Text("Native (LegacyPrefs)")
                .font(.headline)
            Toggle("Dark mode", isOn: $isDark)
                .accessibilityIdentifier("nativeDarkMode")
                .onChange(of: isDark) { LegacyPrefs.shared.isDarkMode = $0 }
            TextField("Native user name", text: $userName)
                .textFieldStyle(.roundedBorder)
                .onChange(of: userName) { LegacyPrefs.shared.userName = $0 }
            Text("Native launch count: \(count)")
        }
        .padding()
        .preferredColorScheme(isDark ? .dark : .light)
    }
}
