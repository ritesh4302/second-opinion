package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/** Fetches a single case by id. */
class GetCaseUseCase(private val caseRepository: CaseRepository) {

    suspend operator fun invoke(caseId: String): SymptomCase? = caseRepository.getCase(caseId)
}
