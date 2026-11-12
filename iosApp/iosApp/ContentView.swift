import SwiftUI
import Shared

struct ContentView: View {
    @State private var isDark: Bool = LegacyPrefs.shared.isDarkMode
    @State private var userName: String = LegacyPrefs.shared.userName
    @State private var count: Int = LegacyPrefs.shared.launchCount

    @State private var darkWatch: Shared.Cancellable?
    @State private var nameWatch: Shared.Cancellable?
    @State private var countWatch: Shared.Cancellable?

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

            ComposeView()
                .frame(height: 300)
        }
        .padding()
        .preferredColorScheme(isDark ? .dark : .light)
        .onAppear {
            let repo = IosStorage.shared.repo
            darkWatch = repo.watchBoolean(key: PrefKeys.shared.darkMode) { value in
                isDark = value.boolValue
            }
            nameWatch = repo.watchString(key: PrefKeys.shared.userName) { value in
                userName = value
            }
            countWatch = repo.watchInt(key: PrefKeys.shared.launchCount) { value in
                count = Int(value.intValue)
            }
        }
        .onDisappear {
            darkWatch?.cancel()
            nameWatch?.cancel()
            countWatch?.cancel()
        }
    }
}
