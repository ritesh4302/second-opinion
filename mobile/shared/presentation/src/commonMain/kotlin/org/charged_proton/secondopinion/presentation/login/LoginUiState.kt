package org.charged_proton.secondopinion.presentation.login

/** Error kinds; the UI layer maps these to localized strings. */
enum class LoginError {
    SIGN_IN_FAILED,
}

data class LoginUiState(
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
)
