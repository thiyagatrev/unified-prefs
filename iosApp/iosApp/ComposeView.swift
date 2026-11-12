import SwiftUI
import Shared

// Embeds the shared Compose Multiplatform screen (MainViewController from the Shared framework).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
