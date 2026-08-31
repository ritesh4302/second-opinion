import AVFoundation
import SwiftUI
import SharedKit

/// Symptom-capture screen mirroring Android's SymptomScreen: tap-to-confirm
/// patient consent, mic-permission check, record/stop, then navigation to the
/// assessment for the created case.
struct RecordView: View {
    let graph: IosAppGraph
    let onSignOut: () -> Void
    @StateObject private var observer: ViewModelObserver<SymptomViewModel, SymptomUiState>
    @State private var assessmentCaseId: String?

    init(graph: IosAppGraph, onSignOut: @escaping () -> Void) {
        self.graph = graph
        self.onSignOut = onSignOut
        let vm = graph.symptomViewModel()
        _observer = StateObject(wrappedValue: ViewModelObserver(vm, vm.uiState))
    }

    private var state: SymptomUiState { observer.state }
    private var vm: SymptomViewModel { observer.vm }

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Text(statusText)
                .font(.headline)
                .foregroundStyle(state.status == .error ? .red : .secondary)

            Button {
                state.isRecording ? vm.onStopRecording() : vm.onRecordRequested()
            } label: {
                Image(systemName: state.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                    .font(.system(size: 96))
                    .foregroundStyle(state.isRecording ? .red : .accentColor)
            }

            if state.status == .saved, let caseId = state.lastCaseId {
                Button("Get assessment") { assessmentCaseId = caseId }
                    .buttonStyle(.borderedProminent)
            }
            Spacer()
        }
        .padding(24)
        .navigationTitle("Record")
        .toolbar {
            Button("Sign out", action: onSignOut)
        }
        .confirmationDialog(
            "Patient consent",
            isPresented: Binding(
                get: { state.awaitingConsent },
                set: { if !$0 { vm.onConsentDeclined() } }
            ),
            titleVisibility: .visible
        ) {
            Button("Patient consents to recording") { confirmConsentAndStart() }
            Button("Cancel", role: .cancel) { vm.onConsentDeclined() }
        } message: {
            Text("Confirm the patient has agreed to this conversation being recorded for a preliminary assessment.")
        }
        .navigationDestination(item: $assessmentCaseId) { caseId in
            AssessmentView(graph: graph, caseId: caseId)
        }
    }

    private var statusText: String {
        switch state.status {
        case .recording: return "Recording… tap to stop"
        case .saved: return "Recording saved"
        case .permissionRequired: return "Microphone access is required — enable it in Settings."
        case .consentDeclined: return "Recording needs patient consent"
        case .error: return "Something went wrong. Try again."
        default: return "Tap to record the symptom conversation"
        }
    }

    /// Consent confirmed: check/request the mic permission, then start.
    private func confirmConsentAndStart() {
        vm.onConsentConfirmed()
        let session = AVAudioSession.sharedInstance()
        switch session.recordPermission {
        case .granted:
            vm.onStartRecording()
        case .denied:
            vm.onPermissionDenied()
        default:
            session.requestRecordPermission { granted in
                DispatchQueue.main.async {
                    granted ? vm.onStartRecording() : vm.onPermissionDenied()
                }
            }
        }
    }
}

extension String: @retroactive Identifiable {
    public var id: String { self }
}
