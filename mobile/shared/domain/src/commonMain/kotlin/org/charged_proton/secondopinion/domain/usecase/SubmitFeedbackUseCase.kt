package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

/** Records the pharmacist's decision on an assessment (feedback loop). */
class SubmitFeedbackUseCase(private val assessmentRepository: AssessmentRepository) {

    suspend operator fun invoke(feedback: Feedback): Result<Unit> =
        assessmentRepository.submitFeedback(feedback)
}
