package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.legal.CURRENT_LEGAL_VERSION
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository

class GetLegalAcceptanceUseCase(private val repository: LegalConsentRepository) {
    operator fun invoke(userId: String): Boolean =
        repository.getAcceptance(userId)?.version == CURRENT_LEGAL_VERSION
}