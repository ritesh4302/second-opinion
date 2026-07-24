package org.charged_proton.secondopinion.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.auth.EmailAuthError
import org.charged_proton.secondopinion.domain.auth.EmailAuthException
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase
import org.charged_proton.secondopinion.domain.usecase.SignInWithEmailUseCase
import org.charged_proton.secondopinion.domain.usecase.SignUpWithEmailUseCase

/**
 * Drives sign-in: the Google button launches the interactive picker flow, and
 * the email/password form signs in to (or, in sign-up mode, creates) a
 * Firebase email account. Successful sign-in flips the [AuthClient] session
 * state, which the app-level auth gate observes — this ViewModel does not
 * navigate itself.
 */
class LoginViewModel(
    private val signIn: SignInUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onToggleSignUp() {
        _uiState.update { it.copy(isSignUp = !it.isSignUp, error = null) }
    }

    fun onSignIn() = submit { signIn() }

    fun onSubmitEmail() {
        val state = _uiState.value
        if (!state.canSubmitEmail) return
        submit {
            if (state.isSignUp) {
                signUpWithEmail(state.email, state.password)
            } else {
                signInWithEmail(state.email, state.password)
            }
        }
    }

    private fun submit(authCall: suspend () -> Result<AuthUser>) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            authCall()
                .onSuccess { _uiState.update { it.copy(isSubmitting = false) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, error = e.toLoginError()) }
                }
        }
    }
}

private fun Throwable.toLoginError(): LoginError? = when (this) {
    // A dismissed account picker is not an error worth surfacing
    is SignInCancelledException -> null
    is EmailAuthException -> when (error) {
        EmailAuthError.INVALID_CREDENTIALS -> LoginError.INVALID_CREDENTIALS
        EmailAuthError.EMAIL_ALREADY_IN_USE -> LoginError.EMAIL_ALREADY_IN_USE
        EmailAuthError.WEAK_PASSWORD -> LoginError.WEAK_PASSWORD
    }
    else -> LoginError.SIGN_IN_FAILED
}
