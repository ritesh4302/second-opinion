package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthUser

/** Verifies the one-time code and signs the pharmacist in. */
class VerifyOtpUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke(code: String): Result<AuthUser> =
        authClient.verifyOtp(code)
}
