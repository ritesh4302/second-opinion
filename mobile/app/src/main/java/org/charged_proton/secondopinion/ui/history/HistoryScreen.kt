package org.charged_proton.secondopinion.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.presentation.history.HistoryViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

/** Case list, newest first; tap a case to open its assessment. */
@Composable
fun HistoryScreen(
    onOpenCase: (caseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.confirmingDeleteCaseId != null) {
        DeleteCaseDialog(
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }

    if (uiState.cases.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.cases, key = SymptomCase::id) { case ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCase(case.id) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateFormat.getDateTimeInstance()
                                .format(Date(case.createdAtEpochMillis)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(case.status.toStringRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.onTogglePlayback(case) }) {
                        Text(
                            text = stringResource(
                                if (case.id == uiState.playingCaseId) {
                                    R.string.stop_playback
                                } else {
                                    R.string.play_recording
                                }
                            )
                        )
                    }
                    TextButton(onClick = { viewModel.onDeleteRequested(case) }) {
                        Text(
                            text = stringResource(R.string.delete_case),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** DPDP erasure: confirm before permanently deleting a case everywhere. */
@Composable
private fun DeleteCaseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_case_title)) },
        text = { Text(stringResource(R.string.delete_case_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_case_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_case_cancel))
            }
        },
    )
}

private fun CaseStatus.toStringRes(): Int = when (this) {
    CaseStatus.RECORDED -> R.string.case_status_recorded
    CaseStatus.UPLOADING -> R.string.case_status_uploading
    CaseStatus.PROCESSING -> R.string.case_status_processing
    CaseStatus.COMPLETED -> R.string.case_status_completed
    CaseStatus.FAILED -> R.string.case_status_failed
}
