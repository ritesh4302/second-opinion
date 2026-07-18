package org.charged_proton.secondopinion.presentation.assessment

import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage

data class AssessmentUiState(
    /** Non-null while the pipeline is running; null once completed or failed. */
    val stage: PipelineStage? = null,
    val assessment: Assessment? = null,
    val errorMessage: String? = null,
    /** Decision already recorded for this assessment, if any. */
    val decision: PharmacistDecision? = null,
    val isSubmittingDecision: Boolean = false,
)
