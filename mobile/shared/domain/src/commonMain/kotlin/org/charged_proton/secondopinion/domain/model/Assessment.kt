package org.charged_proton.secondopinion.domain.model

/**
 * Preliminary assessment returned by the backend pipeline
 * (GET /v1/recordings/{id}/assessment — BACKEND.md §3).
 *
 * Positioning (decision D1): triage + decision support — never a diagnosis
 * or prescription. The pharmacist is always the final authority.
 */
data class Assessment(
    val id: String,
    val caseId: String,
    val symptomSummary: String,
    val conditions: List<ConditionHypothesis>,
    val redFlags: List<RedFlag>,
    val otcGuidance: List<OtcAdvice>,
    val disclaimer: String,
)

/** A possible condition category with a confidence level — not a diagnosis. */
data class ConditionHypothesis(
    val name: String,
    val confidencePercent: Int,
    val rationale: String,
)

/** Danger sign requiring escalation to a doctor. */
data class RedFlag(
    val description: String,
    val action: String,
)

/** OTC-only guidance — scheduled (H/H1) drugs are never suggested. */
data class OtcAdvice(
    val medicine: String,
    val dosage: String,
    val note: String,
)
