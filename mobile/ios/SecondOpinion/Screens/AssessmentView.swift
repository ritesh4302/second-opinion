import SwiftUI
import SharedKit

/// Assessment screen mirroring Android's AssessmentScreen: queue/pipeline
/// progress, the assessment result, and the pharmacist decision buttons.
struct AssessmentView: View {
    @StateObject private var observer: ViewModelObserver<AssessmentViewModel, AssessmentUiState>

    init(graph: IosAppGraph, caseId: String) {
        let vm = graph.assessmentViewModel(caseId: caseId)
        _observer = StateObject(wrappedValue: ViewModelObserver(vm, vm.uiState))
    }

    private var state: AssessmentUiState { observer.state }
    private var vm: AssessmentViewModel { observer.vm }

    var body: some View {
        Group {
            if let assessment = state.assessment {
                resultList(assessment)
            } else if let error = state.errorMessage {
                ContentUnavailableView {
                    Label("Assessment failed", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error)
                } actions: {
                    Button("Retry") { vm.onRetry() }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                VStack(spacing: 12) {
                    ProgressView()
                    Text(progressText).foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Assessment")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var progressText: String {
        if state.isQueued {
            return state.queueAttemptCount > 0
                ? "Waiting to retry upload (attempt \(state.queueAttemptCount))…"
                : "Waiting to upload…"
        }
        switch state.stage {
        case .uploading: return "Uploading recording…"
        case .diarizing: return "Separating speakers…"
        case .transcribing: return "Transcribing…"
        case .extracting: return "Understanding symptoms…"
        case .assessing: return "Preparing assessment…"
        default: return "Working…"
        }
    }

    @ViewBuilder
    private func resultList(_ assessment: Assessment) -> some View {
        List {
            Section("Symptom summary") {
                Text(assessment.symptomSummary)
            }
            if !assessment.redFlags.isEmpty {
                Section("Red flags — refer to a doctor") {
                    ForEach(assessment.redFlags, id: \.description_) { flag in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(flag.description_).bold().foregroundStyle(.red)
                            Text(flag.action).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            Section("Possible conditions") {
                ForEach(assessment.conditions, id: \.name) { condition in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(condition.name) — \(condition.confidencePercent)%").bold()
                        Text(condition.rationale).font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }
            Section("Medicine guidance") {
                ForEach(assessment.otcGuidance, id: \.medicine) { advice in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(advice.medicine).bold()
                            if advice.prescription {
                                Text("Prescription (Sch. H)")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(.orange.opacity(0.2), in: Capsule())
                            }
                        }
                        Text(advice.dosage).font(.footnote)
                        Text(advice.note).font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                Text(assessment.disclaimer)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            decisionSection
        }
    }

    @ViewBuilder
    private var decisionSection: some View {
        Section("Your decision") {
            if let decision = state.decision {
                Label(decisionText(decision), systemImage: "checkmark.seal")
            } else {
                HStack {
                    decisionButton("Accept", .accepted, .green)
                    decisionButton("Override", .overridden, .orange)
                    decisionButton("Reject", .rejected, .red)
                }
                .disabled(state.isSubmittingDecision)
            }
        }
    }

    private func decisionButton(
        _ title: String, _ decision: PharmacistDecision, _ tint: Color
    ) -> some View {
        Button(title) { vm.onDecision(decision: decision, note: nil) }
            .buttonStyle(.bordered)
            .tint(tint)
            .frame(maxWidth: .infinity)
    }

    private func decisionText(_ decision: PharmacistDecision) -> String {
        switch decision {
        case .accepted: return "Accepted"
        case .rejected: return "Rejected"
        case .overridden: return "Overridden"
        default: return "Recorded"
        }
    }
}
