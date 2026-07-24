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

/**
 * Port for Firebase Google Sign-In authentication. Adapters:
 * `FakeGoogleAuthClient` (dev/test, pairs with the backend's fake token
 * verifier) and the production client — Credential Manager (Google account
 * picker) → Firebase Auth SDK — whose **Firebase ID token** backs
 * [currentToken]. The app sends [currentToken] as the `Authorization: Bearer`
 * header on every backend call.
 */
interface AuthClient {

    val authState: StateFlow<AuthState>

    /** Runs the interactive Google Sign-In flow (via Firebase Auth) and signs the pharmacist in. */
    suspend fun signIn(): Result<AuthUser>

    /** Backend bearer token (a Firebase ID token in production), or null when signed out. */
    suspend fun currentToken(): String?

    suspend fun signOut()
}
