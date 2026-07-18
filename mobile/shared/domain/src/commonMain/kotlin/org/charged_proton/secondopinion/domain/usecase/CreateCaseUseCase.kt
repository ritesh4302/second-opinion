package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/** Creates a new symptom case from a finished recording. */
class CreateCaseUseCase(private val caseRepository: CaseRepository) {

    suspend operator fun invoke(recording: Recording): Result<SymptomCase> =
        runCatching { caseRepository.createCase(recording) }
}
