package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

/**
 * DPDP right to erasure: deletes a case everywhere — backend recording
 * (audio, transcripts, assessment), local audio file, and local case entry.
 */
class DeleteCaseUseCase(private val assessmentRepository: AssessmentRepository) {

    suspend operator fun invoke(caseId: String): Result<Unit> =
        assessmentRepository.deleteCase(caseId)
}
