package org.charged_proton.secondopinion.ui.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.RedFlag
import org.charged_proton.secondopinion.presentation.assessment.AssessmentViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Pipeline progress → assessment result → pharmacist decision, for one case. */
@Composable
fun AssessmentScreen(
    caseId: String,
    modifier: Modifier = Modifier,
    viewModel: AssessmentViewModel = koinViewModel { parametersOf(caseId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        uiState.stage?.let { stage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.assessment_in_progress, stage.name),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        uiState.assessment?.let { assessment ->
            if (assessment.redFlags.isNotEmpty()) {
                ReferralBanner(assessment.redFlags)
            }

            SectionTitle(stringResource(R.string.section_summary))
            Text(assessment.symptomSummary, style = MaterialTheme.typography.bodyLarge)

            SectionTitle(stringResource(R.string.section_conditions))
            assessment.conditions.forEach { condition ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${condition.name} — ${condition.confidencePercent}%",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(condition.rationale, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (assessment.otcGuidance.isNotEmpty()) {
                SectionTitle(stringResource(R.string.section_otc))
                assessment.otcGuidance.forEach { advice ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    advice.medicine,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (advice.prescription) {
                                    Text(
                                        text = stringResource(R.string.prescription_drug_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(advice.dosage, style = MaterialTheme.typography.bodyMedium)
                            Text(advice.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                assessment.disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))
            DecisionBar(
                decision = uiState.decision,
                isSubmitting = uiState.isSubmittingDecision,
                onDecision = viewModel::onDecision,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
}

/** Prominent "refer to a doctor" escalation banner, shown above all other content. */
@Composable
private fun ReferralBanner(redFlags: List<RedFlag>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.refer_to_doctor),
                style = MaterialTheme.typography.titleMedium,
            )
            redFlags.forEach { flag ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(flag.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(flag.action, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Accept / reject / different-action buttons, or the recorded decision once submitted. */
@Composable
private fun DecisionBar(
    decision: PharmacistDecision?,
    isSubmitting: Boolean,
    onDecision: (PharmacistDecision) -> Unit,
) {
    if (decision != null) {
        Text(
            text = stringResource(R.string.decision_recorded, decision.name),
            style = MaterialTheme.typography.titleSmall,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onDecision(PharmacistDecision.ACCEPTED) },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.decision_accept))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onDecision(PharmacistDecision.REJECTED) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.decision_reject))
            }
            OutlinedButton(
                onClick = { onDecision(PharmacistDecision.OVERRIDDEN) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.decision_override))
            }
        }
    }
}
