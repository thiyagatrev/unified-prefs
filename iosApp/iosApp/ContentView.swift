import SwiftUI
import Shared

struct ContentView: View {
    @State private var isDark: Bool = LegacyPrefs.shared.isDarkMode
    @State private var userName: String = LegacyPrefs.shared.userName
    @State private var count: Int = LegacyPrefs.shared.launchCount

    @State private var darkWatch: Shared.Cancellable?
    @State private var nameWatch: Shared.Cancellable?
    @State private var countWatch: Shared.Cancellable?

    // Only user input writes to storage. Values arriving from the watchers update the view state
    // directly, so they are never written back and cannot overwrite a newer edit made elsewhere.
    private var darkBinding: Binding<Bool> {
        Binding(get: { isDark }, set: { isDark = $0; LegacyPrefs.shared.isDarkMode = $0 })
    }

    private var nameBinding: Binding<String> {
        Binding(get: { userName }, set: { userName = $0; LegacyPrefs.shared.userName = $0 })
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("Native (LegacyPrefs)")
                .font(.headline)
            Toggle("Dark mode", isOn: darkBinding)
                .accessibilityIdentifier("nativeDarkMode")
            TextField("Native user name", text: nameBinding)
                .textFieldStyle(.roundedBorder)
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
