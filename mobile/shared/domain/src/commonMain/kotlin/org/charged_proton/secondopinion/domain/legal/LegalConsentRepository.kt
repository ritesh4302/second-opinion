package org.charged_proton.secondopinion.domain.legal

const val CURRENT_LEGAL_VERSION = "2026-08-27-telemetry-1"

data class LegalAcceptance(
    val version: String,
    val acceptedAtEpochMillis: Long,
)

/** Pharmacist-specific acknowledgement of the current legal documents. */
interface LegalConsentRepository {
    fun getAcceptance(userId: String): LegalAcceptance?
    suspend fun accept(userId: String, version: String): LegalAcceptance
}