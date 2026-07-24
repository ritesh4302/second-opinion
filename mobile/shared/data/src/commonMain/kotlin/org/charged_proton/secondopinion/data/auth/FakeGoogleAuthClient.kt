package org.charged_proton.secondopinion.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser

/**
 * Dev/test [AuthClient] that pairs with the backend's fake token verifier
 * (`SO_AUTH_PROVIDER=fake`, BACKEND.md §4): [signIn] succeeds immediately with
 * a fixed Google-style identity, [signInWithEmail]/[signUpWithEmail] with an
 * identity derived from the given email, and the issued bearer token
 * `fake:<uid>:<email>:<displayName>` stands in for the Firebase ID token
 * (issuer `securetoken.google.com/<project>`, audience = project id) that the
 * production adapter (Credential Manager / email-password → Firebase Auth SDK)
 * mints. Replaced once the Firebase project (google-services.json) is
 * provisioned.
 */
class FakeGoogleAuthClient(private val tokenStore: AuthTokenStore) : AuthClient {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        val user = tokenStore.readToken()?.let(::userFromToken)
        _authState.value = user?.let(AuthState::SignedIn) ?: AuthState.SignedOut
    }

    override suspend fun signIn(): Result<AuthUser> =
        signInAs(AuthUser(uid = FAKE_UID, email = FAKE_EMAIL, displayName = FAKE_DISPLAY_NAME))

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        signInAs(AuthUser(uid = FAKE_EMAIL_UID, email = email, displayName = email.substringBefore('@')))

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> =
        signInWithEmail(email, password)

    private fun signInAs(user: AuthUser): Result<AuthUser> {
        tokenStore.writeToken("fake:${user.uid}:${user.email}:${user.displayName}")
        _authState.value = AuthState.SignedIn(user)
        return Result.success(user)
    }

    override suspend fun currentToken(): String? = tokenStore.readToken()

    override suspend fun signOut() {
        tokenStore.clear()
        _authState.value = AuthState.SignedOut
    }

    companion object {
        const val FAKE_UID = "fake-google-user"
        const val FAKE_EMAIL_UID = "fake-email-user"
        const val FAKE_EMAIL = "pharmacist@example.com"
        const val FAKE_DISPLAY_NAME = "Test Pharmacist"

        private fun userFromToken(token: String): AuthUser? {
            val parts = token.split(":", limit = 4)
            return if (parts.size == 4 && parts[0] == "fake") {
                AuthUser(uid = parts[1], email = parts[2], displayName = parts[3])
            } else {
                null
            }
        }
    }
}
