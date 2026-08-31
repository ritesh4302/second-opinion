package org.charged_proton.secondopinion.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.auth.EmailAuthError
import org.charged_proton.secondopinion.domain.auth.EmailAuthException
import org.charged_proton.secondopinion.domain.auth.PasswordResetError
import org.charged_proton.secondopinion.domain.auth.PasswordResetException
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import kotlin.coroutines.resume

/**
 * Production iOS [AuthClient]: adapts the Swift-implemented [IosAuthBridge]
 * (Firebase Auth iOS SDK) to the coroutine/StateFlow port the shared
 * ViewModels consume — the iOS counterpart of Android's `FirebaseAuthClient`.
 * Firebase Auth persists the session itself, so no `AuthTokenStore` is
 * involved; the bridge's `currentToken` returns the SDK-refreshed ID token.
 */
class BridgedAuthClient(private val bridge: IosAuthBridge) : AuthClient {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        bridge.watchAuthState { user ->
            _authState.value = user
                ?.let { AuthState.SignedIn(it.toAuthUser()) }
                ?: AuthState.SignedOut
        }
    }

    override suspend fun signIn(): Result<AuthUser> =
        authCall { onResult -> bridge.signInWithGoogle(onResult) }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        authCall { onResult -> bridge.signInWithEmail(email, password, onResult) }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> =
        authCall { onResult -> bridge.signUpWithEmail(email, password, onResult) }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            bridge.sendPasswordResetEmail(email) { errorCode ->
                val result = when (errorCode) {
                    null -> Result.success(Unit)
                    AuthBridgeError.INVALID_EMAIL ->
                        Result.failure(PasswordResetException(PasswordResetError.INVALID_EMAIL))
                    else -> Result.failure(Exception("Password reset failed: $errorCode"))
                }
                continuation.resume(result)
            }
        }

    override suspend fun currentToken(): String? =
        suspendCancellableCoroutine { continuation ->
            bridge.currentToken { token -> continuation.resume(token) }
        }

    override suspend fun signOut(): Unit =
        suspendCancellableCoroutine { continuation ->
            bridge.signOut { continuation.resume(Unit) }
        }

    private suspend fun authCall(
        call: (onResult: (BridgedAuthUser?, String?) -> Unit) -> Unit,
    ): Result<AuthUser> = suspendCancellableCoroutine { continuation ->
        call { user, errorCode ->
            val result = when {
                user != null -> Result.success(user.toAuthUser())
                else -> Result.failure(errorCode.toAuthException())
            }
            continuation.resume(result)
        }
    }
}

private fun BridgedAuthUser.toAuthUser() =
    AuthUser(uid = uid, email = email, displayName = displayName)

private fun String?.toAuthException(): Exception = when (this) {
    AuthBridgeError.CANCELLED -> SignInCancelledException()
    AuthBridgeError.INVALID_CREDENTIALS -> EmailAuthException(EmailAuthError.INVALID_CREDENTIALS)
    AuthBridgeError.EMAIL_ALREADY_IN_USE -> EmailAuthException(EmailAuthError.EMAIL_ALREADY_IN_USE)
    AuthBridgeError.WEAK_PASSWORD -> EmailAuthException(EmailAuthError.WEAK_PASSWORD)
    else -> Exception("Auth failed: ${this ?: "unknown"}")
}
