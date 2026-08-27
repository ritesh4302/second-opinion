package org.charged_proton.secondopinion.ui.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.charged_proton.secondopinion.R

@Composable
fun LegalConsentScreen(
    isAccepting: Boolean,
    acceptanceFailed: Boolean,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.legal_gate_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.legal_gate_intro))
        LegalWarning(R.string.legal_cds_warning)
        LegalWarning(R.string.legal_responsibility_warning)
        LegalWarning(R.string.legal_emergency_warning)
        LegalWarning(R.string.legal_recording_warning)
        LegalWarning(R.string.legal_data_warning)
        Text(
            stringResource(R.string.legal_draft_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        LegalLinks(modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isAccepting) { acknowledged = !acknowledged },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                enabled = !isAccepting,
            )
            Text(stringResource(R.string.legal_acknowledgement), modifier = Modifier.weight(1f))
        }
        if (acceptanceFailed) {
            Text(
                stringResource(R.string.legal_acceptance_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = onAccept,
            enabled = acknowledged && !isAccepting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (isAccepting) R.string.legal_accepting else R.string.legal_accept_continue,
                ),
            )
        }
    }
}

@Composable
private fun LegalWarning(text: Int) {
    Text("• ${stringResource(text)}", style = MaterialTheme.typography.bodyLarge)
}