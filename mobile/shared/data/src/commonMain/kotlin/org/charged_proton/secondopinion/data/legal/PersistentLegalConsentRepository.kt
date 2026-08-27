package org.charged_proton.secondopinion.data.legal

import org.charged_proton.secondopinion.domain.legal.LegalAcceptance
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository

class PersistentLegalConsentRepository(
    private val store: LegalAcceptanceStore,
    private val currentTimeMillis: () -> Long,
) : LegalConsentRepository {

    override fun getAcceptance(userId: String): LegalAcceptance? {
        val version = store.readVersion(userId) ?: return null
        val acceptedAt = store.readAcceptedAt(userId) ?: return null
        return LegalAcceptance(version, acceptedAt)
    }

    override suspend fun accept(userId: String, version: String): LegalAcceptance {
        require(userId.isNotBlank()) { "A user is required to accept legal terms" }
        val acceptance = LegalAcceptance(version, currentTimeMillis())
        store.write(userId, acceptance.version, acceptance.acceptedAtEpochMillis)
        return acceptance
    }
}