package org.charged_proton.secondopinion.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.auth.InvalidPhoneNumberException

/**
 * Dev/test [AuthClient] that pairs with the backend's fake token verifier
 * (`SO_AUTH_PROVIDER=fake`, BACKEND.md §4): any E.164 phone number signs in
 * with the fixed code [FAKE_OTP], and the issued bearer token is
 * `fake:<uid>:<phone>`. Replaced by the Firebase phone-auth adapter once the
 * Firebase project (google-services.json) is provisioned.
 */
class FakeOtpAuthClient(private val tokenStore: AuthTokenStore) : AuthClient {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var pendingPhoneNumber: String? = null

    init {
        val user = tokenStore.readToken()?.let(::userFromToken)
        _authState.value = user?.let(AuthState::SignedIn) ?: AuthState.SignedOut
    }

    override suspend fun requestOtp(phoneNumber: String): Result<Unit> {
        if (!E164_REGEX.matches(phoneNumber)) {
            return Result.failure(InvalidPhoneNumberException(phoneNumber))
        }
        pendingPhoneNumber = phoneNumber
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(code: String): Result<AuthUser> {
        val phoneNumber = pendingPhoneNumber
            ?: return Result.failure(IllegalStateException("Request an OTP before verifying"))
        if (code != FAKE_OTP) return Result.failure(InvalidOtpException())
        val user = AuthUser(uid = "fake-${phoneNumber.removePrefix("+")}", phoneNumber = phoneNumber)
        tokenStore.writeToken("fake:${user.uid}:${user.phoneNumber}")
        pendingPhoneNumber = null
        _authState.value = AuthState.SignedIn(user)
        return Result.success(user)
    }

    override suspend fun currentToken(): String? = tokenStore.readToken()

    override suspend fun signOut() {
        tokenStore.clear()
        pendingPhoneNumber = null
        _authState.value = AuthState.SignedOut
    }

    companion object {
        const val FAKE_OTP = "123456"
        private val E164_REGEX = Regex("""\+[1-9][0-9]{6,14}""")

        private fun userFromToken(token: String): AuthUser? {
            val parts = token.split(":")
            return if (parts.size == 3 && parts[0] == "fake") AuthUser(parts[1], parts[2]) else null
        }
    }
}
