package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

/** Submits a case for assessment and streams pipeline progress to completion. */
class RequestAssessmentUseCase(private val assessmentRepository: AssessmentRepository) {

    operator fun invoke(caseId: String): Flow<AssessmentProgress> =
        assessmentRepository.requestAssessment(caseId)
}
