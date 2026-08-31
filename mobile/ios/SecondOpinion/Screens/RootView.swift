import SwiftUI
import SharedKit

/// App-level gate mirroring Android's root composable: auth session first,
/// then the one-time legal consent, then the main tab navigation.
struct RootView: View {
    let graph: IosAppGraph
    @StateObject private var auth: ViewModelObserver<AuthViewModel, AuthState>

    init(graph: IosAppGraph) {
        self.graph = graph
        let vm = graph.authViewModel()
        _auth = StateObject(wrappedValue: ViewModelObserver(vm, vm.authState))
    }

    var body: some View {
        Group {
            switch auth.state {
            case is AuthStateUnknown:
                ProgressView()
            case let signedIn as AuthStateSignedIn:
                SignedInView(graph: graph, user: signedIn.user) {
                    auth.vm.onSignOut()
                }
                .id(signedIn.user.uid)
            default:
                LoginView(graph: graph)
            }
        }
        .onChange(of: auth.state is AuthStateSignedIn, initial: true) { _, isSignedIn in
            // Mirrors Android's AuthGate: no telemetry while signed out.
            if !isSignedIn {
                TelemetryController.shared.setCollectionEnabled(false)
            }
        }
    }
}

/// Legal-consent gate + main navigation for a signed-in pharmacist.
private struct SignedInView: View {
    let graph: IosAppGraph
    let user: AuthUser
    let onSignOut: () -> Void
    @StateObject private var legal: ViewModelObserver<LegalConsentViewModel, LegalConsentUiState>

    init(graph: IosAppGraph, user: AuthUser, onSignOut: @escaping () -> Void) {
        self.graph = graph
        self.user = user
        self.onSignOut = onSignOut
        let vm = graph.legalConsentViewModel(userId: user.uid)
        _legal = StateObject(wrappedValue: ViewModelObserver(vm, vm.uiState))
    }

    var body: some View {
        Group {
            if legal.state.isAccepted {
                MainTabView(graph: graph, onSignOut: onSignOut)
            } else {
                LegalConsentView(observer: legal)
            }
        }
        .onChange(of: legal.state.isAccepted, initial: true) { _, isAccepted in
            // Mirrors Android's LegalGate: telemetry follows the acceptance
            // of the current legal version.
            TelemetryController.shared.setCollectionEnabled(isAccepted)
        }
    }
}

/// Record + History tabs, mirroring the Android bottom navigation.
private struct MainTabView: View {
    let graph: IosAppGraph
    let onSignOut: () -> Void

    var body: some View {
        TabView {
            NavigationStack {
                RecordView(graph: graph, onSignOut: onSignOut)
            }
            .tabItem { Label("Record", systemImage: "mic.fill") }

            NavigationStack {
                HistoryView(graph: graph)
            }
            .tabItem { Label("History", systemImage: "clock.fill") }
        }
    }
}
