package org.charged_proton.secondopinion.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.auth.InvalidPhoneNumberException
import org.charged_proton.secondopinion.domain.usecase.RequestOtpUseCase
import org.charged_proton.secondopinion.domain.usecase.VerifyOtpUseCase

/**
 * Drives the two-step phone sign-in: request an OTP for the entered number,
 * then verify the code. Successful verification flips the [AuthClient]
 * session state, which the app-level auth gate observes — this ViewModel
 * does not navigate itself.
 */
class LoginViewModel(
    private val requestOtp: RequestOtpUseCase,
    private val verifyOtp: VerifyOtpUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneNumberChanged(value: String) {
        _uiState.update { it.copy(phoneNumber = value, error = null) }
    }

    fun onOtpChanged(value: String) {
        _uiState.update { it.copy(otp = value, error = null) }
    }

    fun onRequestOtp() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            requestOtp(_uiState.value.phoneNumber.trim())
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, step = LoginStep.OTP, otp = "")
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, error = e.toLoginError()) }
                }
        }
    }

    fun onVerifyOtp() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            verifyOtp(_uiState.value.otp.trim())
                .onSuccess { _uiState.update { it.copy(isSubmitting = false) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, error = e.toLoginError()) }
                }
        }
    }

    /** Back from the OTP step to re-enter the phone number. */
    fun onEditPhoneNumber() {
        _uiState.update { it.copy(step = LoginStep.PHONE, otp = "", error = null) }
    }
}

private fun Throwable.toLoginError(): LoginError = when (this) {
    is InvalidPhoneNumberException -> LoginError.INVALID_PHONE
    is InvalidOtpException -> LoginError.INVALID_OTP
    else -> LoginError.NETWORK
}
