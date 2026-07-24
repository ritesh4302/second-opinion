package org.charged_proton.secondopinion.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase

/**
 * Drives Google Sign-In: a single button launches the interactive flow.
 * Successful sign-in flips the [AuthClient] session state, which the
 * app-level auth gate observes — this ViewModel does not navigate itself.
 */
class LoginViewModel(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onSignIn() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            signIn()
                .onSuccess { _uiState.update { it.copy(isSubmitting = false) } }
                .onFailure { e ->
                    // A dismissed account picker is not an error worth surfacing
                    val error =
                        if (e is SignInCancelledException) null else LoginError.SIGN_IN_FAILED
                    _uiState.update { it.copy(isSubmitting = false, error = error) }
                }
        }
    }
}
