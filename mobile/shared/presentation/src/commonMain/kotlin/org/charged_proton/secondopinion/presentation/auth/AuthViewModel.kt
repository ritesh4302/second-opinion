package org.charged_proton.secondopinion.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.usecase.ObserveAuthStateUseCase
import org.charged_proton.secondopinion.domain.usecase.SignOutUseCase

/**
 * Exposes the auth session for the app-level login gate: signed out shows
 * the login screen, signed in shows the main navigation. Also carries the
 * sign-out action for the in-app affordance — signing out flips the session
 * state, which the gate observes; this ViewModel does not navigate itself.
 */
class AuthViewModel(
    observeAuthState: ObserveAuthStateUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    val authState: StateFlow<AuthState> = observeAuthState()

    fun onSignOut() {
        viewModelScope.launch { signOut() }
    }
}
