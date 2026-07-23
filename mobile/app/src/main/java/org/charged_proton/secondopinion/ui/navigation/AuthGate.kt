package org.charged_proton.secondopinion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.presentation.auth.AuthViewModel
import org.charged_proton.secondopinion.ui.login.LoginScreen
import org.koin.androidx.compose.koinViewModel

/**
 * App-level login gate: shows [LoginScreen] until the session is signed in,
 * then the main [AppNavHost]. A 401 from the backend signs the session out,
 * which lands back here automatically.
 */
@Composable
fun AuthGate(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Unknown -> Unit // blank frame while the session restores
        AuthState.SignedOut -> LoginScreen(modifier = modifier)
        is AuthState.SignedIn -> AppNavHost(modifier = modifier)
    }
}
