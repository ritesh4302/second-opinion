package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.auth.AuthClient

/** Signs the pharmacist out; the app-level auth gate lands back on the login screen. */
class SignOutUseCase(private val authClient: AuthClient) {

    suspend operator fun invoke() = authClient.signOut()
}
