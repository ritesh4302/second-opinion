package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthUser

/** Signs in to an existing email/password account. */
class SignInWithEmailUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke(email: String, password: String): Result<AuthUser> =
        authClient.signInWithEmail(email.trim(), password)
}
