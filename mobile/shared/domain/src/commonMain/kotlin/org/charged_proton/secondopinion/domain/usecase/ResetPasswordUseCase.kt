package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.PasswordResetError
import org.charged_proton.secondopinion.domain.auth.PasswordResetException

/** Requests a reset link for a normalized email address. */
class ResetPasswordUseCase(private val authClient: AuthClient) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val normalized = email.trim()
        if (!EMAIL_PATTERN.matches(normalized)) {
            return Result.failure(PasswordResetException(PasswordResetError.INVALID_EMAIL))
        }
        return authClient.sendPasswordResetEmail(normalized)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}