package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthUser

/** Creates an email/password account and signs the pharmacist in. */
class SignUpWithEmailUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke(email: String, password: String): Result<AuthUser> =
        authClient.signUpWithEmail(email.trim(), password)
}
