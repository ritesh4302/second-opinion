package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

/** Fetches a previously submitted decision for an assessment, if any. */
class GetFeedbackUseCase(private val assessmentRepository: AssessmentRepository) {

    suspend operator fun invoke(assessmentId: String): Feedback? =
        assessmentRepository.getFeedback(assessmentId)
}
