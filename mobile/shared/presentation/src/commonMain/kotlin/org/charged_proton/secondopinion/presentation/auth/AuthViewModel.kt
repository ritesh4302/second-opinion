package org.charged_proton.secondopinion.presentation.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.usecase.ObserveAuthStateUseCase

/**
 * Exposes the auth session for the app-level login gate: signed out shows
 * the login screen, signed in shows the main navigation.
 */
class AuthViewModel(observeAuthState: ObserveAuthStateUseCase) : ViewModel() {

    val authState: StateFlow<AuthState> = observeAuthState()
}
