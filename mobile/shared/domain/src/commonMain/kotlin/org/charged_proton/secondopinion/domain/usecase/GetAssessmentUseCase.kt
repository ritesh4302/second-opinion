package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

/** Fetches the completed assessment for a case, if available. */
class GetAssessmentUseCase(private val assessmentRepository: AssessmentRepository) {

    suspend operator fun invoke(caseId: String): Assessment? =
        assessmentRepository.getAssessment(caseId)
}
