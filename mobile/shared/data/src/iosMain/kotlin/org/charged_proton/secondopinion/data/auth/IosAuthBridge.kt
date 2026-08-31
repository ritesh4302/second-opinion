package org.charged_proton.secondopinion.data.auth

/**
 * Identity reported by the Swift-side auth provider (Firebase Auth iOS SDK).
 * Mirrors `AuthUser` but stays a plain bridge type so Swift never constructs
 * domain models directly.
 */
data class BridgedAuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Error codes the Swift bridge reports on failed auth calls; `BridgedAuthClient`
 * maps them to the typed domain exceptions the shared ViewModels expect.
 * Any other (or null) code becomes a generic failure.
 */
object AuthBridgeError {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val EMAIL_ALREADY_IN_USE = "EMAIL_ALREADY_IN_USE"
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val INVALID_EMAIL = "INVALID_EMAIL"
    const val CANCELLED = "CANCELLED"
}

/**
 * Callback-based port implemented in Swift (FirebaseAuthBridge) on top of the
 * Firebase Auth iOS SDK. Callback style rather than suspend so the Swift
 * implementation stays plain completion-handler code; `BridgedAuthClient`
 * adapts it to the coroutine-based [org.charged_proton.secondopinion.domain.auth.AuthClient].
 *
 * All callbacks may be invoked on any thread.
 */
interface IosAuthBridge {

    /** Whether the interactive Google sign-in flow is available (GoogleSignIn SDK wired). */
    val supportsGoogleSignIn: Boolean

    /**
     * Registers for auth-state changes. Must fire immediately with the
     * persisted session (or null), then on every sign-in/sign-out.
     */
    fun watchAuthState(onChange: (BridgedAuthUser?) -> Unit)

    /** Runs the interactive Google sign-in flow. */
    fun signInWithGoogle(onResult: (BridgedAuthUser?, String?) -> Unit)

    fun signInWithEmail(
        email: String,
        password: String,
        onResult: (BridgedAuthUser?, String?) -> Unit,
    )

    fun signUpWithEmail(
        email: String,
        password: String,
        onResult: (BridgedAuthUser?, String?) -> Unit,
    )

    /** onResult receives an [AuthBridgeError] code, or null on success. */
    fun sendPasswordResetEmail(email: String, onResult: (String?) -> Unit)

    /** Current Firebase ID token (refreshed by the SDK), or null when signed out. */
    fun currentToken(onResult: (String?) -> Unit)

    fun signOut(onDone: () -> Unit)
}
