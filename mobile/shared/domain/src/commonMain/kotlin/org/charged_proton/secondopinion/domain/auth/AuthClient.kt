package org.charged_proton.secondopinion.domain.auth

import kotlinx.coroutines.flow.StateFlow

/** Signed-in pharmacist identity as reported by the auth provider. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

sealed interface AuthState {
    /** Session restore from persistent storage has not finished yet. */
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** The user dismissed the Google account picker without choosing an account. */
class SignInCancelledException : Exception("Sign-in cancelled")

/** Why an email/password sign-in or sign-up attempt was rejected. */
enum class EmailAuthError {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
}

class EmailAuthException(val error: EmailAuthError) : Exception("Email auth failed: $error")

enum class PasswordResetError {
    INVALID_EMAIL,
}

class PasswordResetException(val error: PasswordResetError) :
    Exception("Password reset failed: $error")

/**
 * Port for Firebase authentication (Google Sign-In plus email/password).
 * Adapters: `FakeGoogleAuthClient` (dev/test, pairs with the backend's fake
 * token verifier) and the production client — Credential Manager (Google
 * account picker) / Firebase email-password auth → Firebase Auth SDK — whose
 * **Firebase ID token** backs [currentToken]. The app sends [currentToken] as
 * the `Authorization: Bearer` header on every backend call.
 */
interface AuthClient {

    val authState: StateFlow<AuthState>

    /** Runs the interactive Google Sign-In flow (via Firebase Auth) and signs the pharmacist in. */
    suspend fun signIn(): Result<AuthUser>

    /** Signs in to an existing email/password account; fails with [EmailAuthException] on rejection. */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    /** Creates an email/password account and signs the pharmacist in. */
    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>

    /** Sends a password-reset link without revealing whether the account exists. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Backend bearer token (a Firebase ID token in production), or null when signed out. */
    suspend fun currentToken(): String?

    suspend fun signOut()
}
