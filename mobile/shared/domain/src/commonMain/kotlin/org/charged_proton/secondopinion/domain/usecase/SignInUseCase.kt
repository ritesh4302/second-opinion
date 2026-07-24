package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthUser

/** Runs the Google Sign-In flow and signs the pharmacist in. */
class SignInUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke(): Result<AuthUser> = authClient.signIn()
}
