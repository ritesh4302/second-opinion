package org.charged_proton.secondopinion.presentation.login

/** Error kinds; the UI layer maps these to localized strings. */
enum class LoginError {
    SIGN_IN_FAILED,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    PASSWORD_RESET_FAILED,
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    /** false = sign in to an existing account, true = create a new one. */
    val isSignUp: Boolean = false,
    val isSubmitting: Boolean = false,
    val isResettingPassword: Boolean = false,
    val passwordResetSent: Boolean = false,
    val error: LoginError? = null,
) {
    val isBusy: Boolean
        get() = isSubmitting || isResettingPassword

    val canSubmitEmail: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isBusy

    val canResetPassword: Boolean
        get() = !isSignUp && email.isNotBlank() && !isBusy
}
