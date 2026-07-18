package org.charged_proton.secondopinion.domain.model

/**
 * Pharmacist decision on an assessment — the feedback loop
 * (POST /v1/assessments/{id}/feedback — BACKEND.md §3).
 */
data class Feedback(
    val assessmentId: String,
    val decision: PharmacistDecision,
    val note: String? = null,
)

enum class PharmacistDecision {
    ACCEPTED,
    REJECTED,
    OVERRIDDEN,
}
