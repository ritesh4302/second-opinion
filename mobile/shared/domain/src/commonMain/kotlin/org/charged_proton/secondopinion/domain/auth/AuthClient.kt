package org.charged_proton.secondopinion.domain.auth

import kotlinx.coroutines.flow.StateFlow

/** Signed-in pharmacist identity as reported by the auth provider. */
data class AuthUser(
    val uid: String,
    val phoneNumber: String,
)

sealed interface AuthState {
    /** Session restore from persistent storage has not finished yet. */
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** [phoneNumber] did not look like an E.164 number (e.g. +919876543210). */
class InvalidPhoneNumberException(val phoneNumber: String) :
    Exception("Invalid phone number: $phoneNumber")

/** The one-time code was wrong or expired. */
class InvalidOtpException : Exception("Invalid one-time code")

/**
 * Port for phone-number (OTP) authentication. Adapters: `FakeOtpAuthClient`
 * (dev/test, pairs with the backend's fake token verifier) and a Firebase
 * phone-auth client (production). The app sends [currentToken] as the
 * `Authorization: Bearer` header on every backend call.
 */
interface AuthClient {

    val authState: StateFlow<AuthState>

    /** Sends a one-time code to [phoneNumber] (E.164, e.g. +919876543210). */
    suspend fun requestOtp(phoneNumber: String): Result<Unit>

    /** Verifies the code from the last [requestOtp] call and signs in. */
    suspend fun verifyOtp(code: String): Result<AuthUser>

    /** Backend bearer token for the current session, or null when signed out. */
    suspend fun currentToken(): String?

    suspend fun signOut()
}
