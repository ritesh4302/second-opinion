package org.charged_proton.secondopinion.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.presentation.symptom.SymptomStatus
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.koin.androidx.compose.koinViewModel

/** Symptom capture: record, then hand the created case off to assessment. */
@Composable
fun RecordScreen(
    onOpenAssessment: (caseId: String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SymptomViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onStartRecording() else viewModel.onPermissionDenied()
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
        Button(
            onClick = {
                when {
                    uiState.isRecording -> viewModel.onStopRecording()
                    hasAudioPermission() -> viewModel.onStartRecording()
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                onClick = { onOpenAssessment(caseId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.get_assessment))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.view_history))
        }
    }
}

private fun SymptomStatus.toStringRes(): Int = when (this) {
    SymptomStatus.IDLE -> R.string.prompt_describe_symptoms
    SymptomStatus.RECORDING -> R.string.status_recording
    SymptomStatus.SAVED -> R.string.status_saved
    SymptomStatus.PERMISSION_REQUIRED -> R.string.status_permission_required
    SymptomStatus.ERROR -> R.string.status_error
}
