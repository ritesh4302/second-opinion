import SwiftUI
import SharedKit

/// One-time legal acceptance gate shown after first sign-in, mirroring
/// Android's LegalConsentScreen. The accepted version is persisted per user.
struct LegalConsentView: View {
    @ObservedObject var observer: ViewModelObserver<LegalConsentViewModel, LegalConsentUiState>

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("Before you start")
                .font(.title.bold())
            Text(
                """
                Second Opinion provides triage and decision support — never a \
                diagnosis or prescription. You, the pharmacist, remain the final \
                authority on every recommendation.

                Recordings are made only after the patient's consent and are \
                processed to prepare a preliminary assessment. Anonymous usage \
                and crash data helps us improve the app.
                """
            )
            .foregroundStyle(.secondary)

            if observer.state.acceptanceFailed {
                Text("Could not save your acceptance. Try again.")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            Button {
                observer.vm.onAccept()
            } label: {
                if observer.state.isAccepting {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Accept and continue").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(observer.state.isAccepting)
            Spacer()
        }
        .padding(24)
    }
}
