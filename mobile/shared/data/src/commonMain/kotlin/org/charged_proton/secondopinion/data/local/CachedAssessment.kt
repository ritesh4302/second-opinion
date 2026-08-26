package org.charged_proton.secondopinion.data.local

import kotlinx.serialization.Serializable
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.RedFlag

@Serializable
internal data class CachedAssessment(
    val id: String,
    val caseId: String,
    val symptomSummary: String,
    val conditions: List<CachedCondition>,
    val redFlags: List<CachedRedFlag>,
    val otcGuidance: List<CachedOtcAdvice>,
    val disclaimer: String,
)

@Serializable
internal data class CachedCondition(
    val name: String,
    val confidencePercent: Int,
    val rationale: String,
)

@Serializable
internal data class CachedRedFlag(val description: String, val action: String)

@Serializable
internal data class CachedOtcAdvice(
    val medicine: String,
    val dosage: String,
    val note: String,
    val prescription: Boolean,
)

internal fun Assessment.toCache() = CachedAssessment(
    id = id,
    caseId = caseId,
    symptomSummary = symptomSummary,
    conditions = conditions.map { CachedCondition(it.name, it.confidencePercent, it.rationale) },
    redFlags = redFlags.map { CachedRedFlag(it.description, it.action) },
    otcGuidance = otcGuidance.map {
        CachedOtcAdvice(it.medicine, it.dosage, it.note, it.prescription)
    },
    disclaimer = disclaimer,
)

internal fun CachedAssessment.toDomain() = Assessment(
    id = id,
    caseId = caseId,
    symptomSummary = symptomSummary,
    conditions = conditions.map { ConditionHypothesis(it.name, it.confidencePercent, it.rationale) },
    redFlags = redFlags.map { RedFlag(it.description, it.action) },
    otcGuidance = otcGuidance.map { OtcAdvice(it.medicine, it.dosage, it.note, it.prescription) },
    disclaimer = disclaimer,
)