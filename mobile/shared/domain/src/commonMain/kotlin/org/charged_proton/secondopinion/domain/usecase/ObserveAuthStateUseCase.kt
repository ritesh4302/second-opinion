package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState

/** Streams the auth session state; drives the login-gate at app start. */
class ObserveAuthStateUseCase(private val authClient: AuthClient) {

    operator fun invoke(): StateFlow<AuthState> = authClient.authState
}
