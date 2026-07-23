package org.charged_proton.secondopinion.presentation.login

enum class LoginStep {
    /** Entering the phone number. */
    PHONE,
    /** Entering the one-time code sent to the phone. */
    OTP,
}

/** Error kinds; the UI layer maps these to localized strings. */
enum class LoginError {
    INVALID_PHONE,
    INVALID_OTP,
    NETWORK,
}

data class LoginUiState(
    val step: LoginStep = LoginStep.PHONE,
    val phoneNumber: String = "",
    val otp: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
)
