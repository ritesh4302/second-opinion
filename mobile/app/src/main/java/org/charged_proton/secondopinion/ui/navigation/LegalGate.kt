package org.charged_proton.secondopinion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.presentation.legal.LegalConsentViewModel
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.charged_proton.secondopinion.telemetry.NoOpAppTelemetry
import org.charged_proton.secondopinion.ui.legal.LegalConsentScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LegalGate(
    userId: String,
    modifier: Modifier = Modifier,
    telemetry: AppTelemetry = NoOpAppTelemetry,
    viewModel: LegalConsentViewModel = koinViewModel(
        key = "legal-consent-$userId",
        parameters = { parametersOf(userId) },
    ),
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isAccepted) {
        telemetry.setCollectionEnabled(uiState.isAccepted)
    }
    if (uiState.isAccepted) {
        content()
    } else {
        LegalConsentScreen(
            isAccepting = uiState.isAccepting,
            acceptanceFailed = uiState.acceptanceFailed,
            onAccept = viewModel::onAccept,
            modifier = modifier,
        )
    }
}