import SwiftUI
import SharedKit

/// Sign-in screen mirroring Android's LoginScreen: Google button plus an
/// email/password form with a sign-up toggle and password reset.
struct LoginView: View {
    @StateObject private var observer: ViewModelObserver<LoginViewModel, LoginUiState>

    init(graph: IosAppGraph) {
        let vm = graph.loginViewModel()
        _observer = StateObject(wrappedValue: ViewModelObserver(vm, vm.uiState))
    }

    private var state: LoginUiState { observer.state }
    private var vm: LoginViewModel { observer.vm }

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("Second Opinion")
                .font(.largeTitle.bold())
            Text("Pharmacist sign-in")
                .foregroundStyle(.secondary)

            Button {
                vm.onSignIn()
            } label: {
                Label("Continue with Google", systemImage: "person.crop.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(state.isBusy)

            HStack {
                VStack { Divider() }
                Text("or").foregroundStyle(.secondary).font(.footnote)
                VStack { Divider() }
            }

            TextField("Email", text: Binding(
                get: { state.email },
                set: { vm.onEmailChange(email: $0) }
            ))
            .textFieldStyle(.roundedBorder)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            SecureField("Password", text: Binding(
                get: { state.password },
                set: { vm.onPasswordChange(password: $0) }
            ))
            .textFieldStyle(.roundedBorder)

            Button {
                vm.onSubmitEmail()
            } label: {
                if state.isSubmitting {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text(state.isSignUp ? "Create account" : "Sign in")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.bordered)
            .disabled(!state.canSubmitEmail)

            HStack {
                Button(state.isSignUp ? "Have an account? Sign in" : "New here? Create account") {
                    vm.onToggleSignUp()
                }
                Spacer()
                if !state.isSignUp {
                    Button("Forgot password?") { vm.onForgotPassword() }
                        .disabled(!state.canResetPassword)
                }
            }
            .font(.footnote)

            if state.passwordResetSent {
                Text("Password reset email sent — check the inbox.")
                    .font(.footnote)
                    .foregroundStyle(.green)
            }
            if let error = state.error {
                Text(errorMessage(error))
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
            Spacer()
        }
        .padding(24)
    }

    private func errorMessage(_ error: LoginError) -> String {
        switch error {
        case .invalidCredentials: return "Email or password is incorrect."
        case .emailAlreadyInUse: return "An account with this email already exists."
        case .weakPassword: return "Password is too weak — use at least 6 characters."
        case .invalidEmail: return "Enter a valid email address."
        case .passwordResetFailed: return "Could not send the reset email. Try again."
        default: return "Sign-in failed. Try again."
        }
    }
}
