import FirebaseCore
import SwiftUI
import SharedKit

@main
struct SecondOpinionApp: App {
    /// Composition root: one shared-code graph for the process lifetime,
    /// mirroring Android's Koin appModule (see IosAppGraph in SharedKit).
    /// Mirrors Android's FirebaseApp presence check: with a valid
    /// GoogleService-Info.plist the Firebase-backed auth bridge is used,
    /// otherwise the graph falls back to the fake client (dev stack,
    /// SO_AUTH_PROVIDER=fake).
    static let graph: IosAppGraph = {
        FirebaseApp.configure()
        let bridge: IosAuthBridge? = FirebaseApp.app().map { _ in FirebaseAuthBridge() }
        return IosAppGraph(backendBaseUrl: AppConfig.backendBaseURL, authBridge: bridge)
    }()

    var body: some Scene {
        WindowGroup {
            RootView(graph: Self.graph)
        }
    }
}
