package org.charged_proton.secondopinion.data.legal

interface LegalAcceptanceStore {
    fun readVersion(userId: String): String?
    fun readAcceptedAt(userId: String): Long?
    suspend fun write(userId: String, version: String, acceptedAtEpochMillis: Long)
}