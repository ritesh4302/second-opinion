package org.charged_proton.secondopinion.presentation.login

/** Error kinds; the UI layer maps these to localized strings. */
enum class LoginError {
    SIGN_IN_FAILED,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    /** false = sign in to an existing account, true = create a new one. */
    val isSignUp: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
) {
    val canSubmitEmail: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}
