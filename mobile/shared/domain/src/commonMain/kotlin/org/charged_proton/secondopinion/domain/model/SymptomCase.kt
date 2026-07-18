package org.charged_proton.secondopinion.domain.model

/**
 * A symptom-capture case: one recording and its journey through the
 * assessment pipeline. Mirrors the backend `recordings` row (BACKEND.md §4).
 */
data class SymptomCase(
    val id: String,
    val recording: Recording,
    val status: CaseStatus,
    val createdAtEpochMillis: Long,
)

enum class CaseStatus {
    RECORDED,
    UPLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
