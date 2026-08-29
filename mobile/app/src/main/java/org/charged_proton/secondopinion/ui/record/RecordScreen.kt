package org.charged_proton.secondopinion.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.presentation.auth.AuthViewModel
import org.charged_proton.secondopinion.presentation.symptom.SymptomStatus
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.charged_proton.secondopinion.telemetry.NoOpAppTelemetry
import org.charged_proton.secondopinion.telemetry.TelemetryEvent
import org.charged_proton.secondopinion.ui.legal.LegalLinks
import org.koin.androidx.compose.koinViewModel

/** Symptom capture: record, then hand the created case off to assessment. */
@Composable
fun RecordScreen(
    onOpenAssessment: (caseId: String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SymptomViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    telemetry: AppTelemetry = NoOpAppTelemetry,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            telemetry.event(TelemetryEvent.RECORDING_STARTED)
            viewModel.onStartRecording()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Signed-in identity + sign-out; signing out flips the auth gate
        // back to the login screen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (authState as? AuthState.SignedIn)
                    ?.user?.let { it.displayName ?: it.email }
                    .orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                telemetry.setCollectionEnabled(false)
                authViewModel.onSignOut()
            }) {
                Text(text = stringResource(R.string.sign_out))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (uiState.isRecording) {
                    telemetry.event(TelemetryEvent.RECORDING_STOPPED)
                    viewModel.onStopRecording()
                } else {
                    viewModel.onRecordRequested()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    if (uiState.isRecording) R.string.stop else R.string.speak
                ),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(uiState.status.toStringRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        uiState.lastCaseId?.let { caseId ->
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    telemetry.event(TelemetryEvent.ASSESSMENT_REQUESTED)
                    onOpenAssessment(caseId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.get_assessment))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.cds_short_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.view_history))
        }
        LegalLinks()
    }

    if (uiState.awaitingConsent) {
        AlertDialog(
            onDismissRequest = viewModel::onConsentDeclined,
            title = { Text(text = stringResource(R.string.consent_title)) },
            text = { Text(text = stringResource(R.string.consent_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onConsentConfirmed()
                        if (hasAudioPermission()) {
                            telemetry.event(TelemetryEvent.RECORDING_STARTED)
                            viewModel.onStartRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.consent_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onConsentDeclined) {
                    Text(text = stringResource(R.string.consent_decline))
                }
            },
        )
    }
}

private fun SymptomStatus.toStringRes(): Int = when (this) {
    SymptomStatus.IDLE -> R.string.prompt_describe_symptoms
    SymptomStatus.RECORDING -> R.string.status_recording
    SymptomStatus.SAVED -> R.string.status_saved
    SymptomStatus.PERMISSION_REQUIRED -> R.string.status_permission_required
    SymptomStatus.CONSENT_DECLINED -> R.string.status_consent_declined
    SymptomStatus.ERROR -> R.string.status_error
}
