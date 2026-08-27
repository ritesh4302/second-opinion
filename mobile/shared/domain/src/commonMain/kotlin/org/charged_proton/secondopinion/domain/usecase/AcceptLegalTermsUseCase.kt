package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.legal.CURRENT_LEGAL_VERSION
import org.charged_proton.secondopinion.domain.legal.LegalAcceptance
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository

class AcceptLegalTermsUseCase(private val repository: LegalConsentRepository) {
    suspend operator fun invoke(userId: String): LegalAcceptance =
        repository.accept(userId, CURRENT_LEGAL_VERSION)
}