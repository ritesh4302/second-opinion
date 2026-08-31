import SwiftUI
import SharedKit

@main
struct SecondOpinionApp: App {
    /// Composition root: one shared-code graph for the process lifetime,
    /// mirroring Android's Koin appModule (see IosAppGraph in SharedKit).
    static let graph = IosAppGraph(backendBaseUrl: AppConfig.backendBaseURL)

    var body: some Scene {
        WindowGroup {
            RootView(graph: Self.graph)
        }
    }
}
