import FirebaseAuth
import Foundation
import SharedKit

/// Firebase Auth implementation of the SharedKit `IosAuthBridge` port —
/// the iOS counterpart of Android's `FirebaseAuthClient`. `BridgedAuthClient`
/// (Kotlin) adapts this into the `AuthClient` the shared ViewModels consume,
/// mapping the `AuthBridgeError` codes to the typed domain exceptions.
final class FirebaseAuthBridge: NSObject, IosAuthBridge {

    /// The GoogleSignIn SDK is not wired yet; LoginView hides the Google
    /// button and pharmacists use email/password (roadmap follow-up).
    var supportsGoogleSignIn: Bool { false }

    func watchAuthState(onChange: @escaping (BridgedAuthUser?) -> Void) {
        // Fires immediately with the persisted session (or nil), then on
        // every sign-in/sign-out — same contract as Android's listener.
        Auth.auth().addStateDidChangeListener { _, user in
            onChange(user.map(Self.bridged))
        }
    }

    func signInWithGoogle(onResult: @escaping (BridgedAuthUser?, String?) -> Void) {
        onResult(nil, AuthBridgeError.shared.CANCELLED)
    }

    func signInWithEmail(
        email: String,
        password: String,
        onResult: @escaping (BridgedAuthUser?, String?) -> Void
    ) {
        Auth.auth().signIn(withEmail: email, password: password) { result, error in
            Self.complete(result, error, onResult)
        }
    }

    func signUpWithEmail(
        email: String,
        password: String,
        onResult: @escaping (BridgedAuthUser?, String?) -> Void
    ) {
        Auth.auth().createUser(withEmail: email, password: password) { result, error in
            Self.complete(result, error, onResult)
        }
    }

    func sendPasswordResetEmail(email: String, onResult: @escaping (String?) -> Void) {
        Auth.auth().sendPasswordReset(withEmail: email) { error in
            guard let error else { return onResult(nil) }
            switch AuthErrorCode(rawValue: (error as NSError).code) {
            case .invalidEmail, .invalidRecipientEmail, .missingEmail:
                onResult(AuthBridgeError.shared.INVALID_EMAIL)
            default:
                onResult("RESET_FAILED")
            }
        }
    }

    func currentToken(onResult: @escaping (String?) -> Void) {
        guard let user = Auth.auth().currentUser else { return onResult(nil) }
        // Can fail offline once the cached token expires; a nil bearer then
        // simply yields an unauthenticated backend call (matches Android).
        user.getIDToken { token, _ in onResult(token) }
    }

    func signOut(onDone: @escaping () -> Void) {
        try? Auth.auth().signOut()
        onDone()
    }

    private static func complete(
        _ result: AuthDataResult?,
        _ error: Error?,
        _ onResult: (BridgedAuthUser?, String?) -> Void
    ) {
        if let user = result?.user {
            onResult(bridged(user), nil)
        } else {
            onResult(nil, errorCode(error))
        }
    }

    private static func bridged(_ user: User) -> BridgedAuthUser {
        BridgedAuthUser(uid: user.uid, email: user.email, displayName: user.displayName)
    }

    /// Mirrors Android's exception mapping: wrong password / unknown user /
    /// malformed email all collapse to INVALID_CREDENTIALS on sign-in.
    private static func errorCode(_ error: Error?) -> String {
        guard let error else { return "NO_USER" }
        switch AuthErrorCode(rawValue: (error as NSError).code) {
        case .wrongPassword, .invalidCredential, .userNotFound, .invalidEmail,
             .userDisabled:
            return AuthBridgeError.shared.INVALID_CREDENTIALS
        case .emailAlreadyInUse:
            return AuthBridgeError.shared.EMAIL_ALREADY_IN_USE
        case .weakPassword:
            return AuthBridgeError.shared.WEAK_PASSWORD
        default:
            return "AUTH_FAILED_\((error as NSError).code)"
        }
    }
}
