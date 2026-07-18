package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/** Streams all symptom cases, newest first. */
class ObserveCasesUseCase(private val caseRepository: CaseRepository) {

    operator fun invoke(): Flow<List<SymptomCase>> = caseRepository.observeCases()
}
