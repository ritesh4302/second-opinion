import SwiftUI
import SharedKit

/// Case history mirroring Android's HistoryScreen: play/stop the recording,
/// open the assessment, delete with confirmation.
struct HistoryView: View {
    let graph: IosAppGraph
    @StateObject private var observer: ViewModelObserver<HistoryViewModel, HistoryUiState>

    init(graph: IosAppGraph) {
        self.graph = graph
        let vm = graph.historyViewModel()
        _observer = StateObject(wrappedValue: ViewModelObserver(vm, vm.uiState))
    }

    private var state: HistoryUiState { observer.state }
    private var vm: HistoryViewModel { observer.vm }

    var body: some View {
        Group {
            if state.cases.isEmpty {
                ContentUnavailableView(
                    "No recordings yet",
                    systemImage: "clock",
                    description: Text("Recorded symptom conversations appear here.")
                )
            } else {
                List {
                    ForEach(state.cases, id: \.id) { symptomCase in
                        row(symptomCase)
                    }
                }
            }
        }
        .navigationTitle("History")
        .confirmationDialog(
            "Delete this case?",
            isPresented: Binding(
                get: { state.confirmingDeleteCaseId != nil },
                set: { if !$0 { vm.onDeleteDismissed() } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) { vm.onDeleteConfirmed() }
            Button("Cancel", role: .cancel) { vm.onDeleteDismissed() }
        } message: {
            Text("The recording and its assessment are removed from this device.")
        }
    }

    @ViewBuilder
    private func row(_ symptomCase: SymptomCase) -> some View {
        HStack(spacing: 12) {
            Button {
                vm.onTogglePlayback(case: symptomCase)
            } label: {
                Image(systemName: state.playingCaseId == symptomCase.id
                    ? "stop.circle.fill" : "play.circle")
                    .font(.title2)
            }
            .buttonStyle(.plain)

            NavigationLink {
                AssessmentView(graph: graph, caseId: symptomCase.id)
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(dateText(symptomCase.createdAtEpochMillis))
                        .font(.body)
                    Text(statusText(symptomCase.status))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .swipeActions {
            Button("Delete", role: .destructive) {
                vm.onDeleteRequested(case: symptomCase)
            }
        }
    }

    private func dateText(_ epochMillis: Int64) -> String {
        Date(timeIntervalSince1970: Double(epochMillis) / 1000)
            .formatted(date: .abbreviated, time: .shortened)
    }

    private func statusText(_ status: CaseStatus) -> String {
        switch status {
        case .recorded: return "Recorded"
        case .queued: return "Waiting to upload"
        case .uploading: return "Uploading"
        case .retrying: return "Upload retrying"
        case .processing: return "Assessing"
        case .completed: return "Assessment ready"
        case .failed: return "Failed"
        default: return "Unknown"
        }
    }
}
