package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient

/** Sends a one-time sign-in code to the pharmacist's phone number. */
class RequestOtpUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke(phoneNumber: String): Result<Unit> =
        authClient.requestOtp(phoneNumber)
}
