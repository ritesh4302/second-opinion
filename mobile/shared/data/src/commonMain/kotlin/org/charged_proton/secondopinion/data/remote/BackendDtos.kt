package org.charged_proton.secondopinion.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.ConditionHypothesis
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.model.RedFlag

/** Wire types mirroring the backend's Pydantic schemas (BACKEND.md §3). */

@Serializable
data class RecordingDto(
    val id: String,
    val status: String,
    @SerialName("failure_stage") val failureStage: String? = null,
)

@Serializable
data class AssessmentDto(
    val id: String,
    @SerialName("recording_id") val recordingId: String,
    @SerialName("symptom_summary") val symptomSummary: String = "",
    val conditions: List<ConditionDto> = emptyList(),
    @SerialName("red_flags") val redFlags: List<RedFlagDto> = emptyList(),
    @SerialName("otc_guidance") val otcGuidance: List<OtcAdviceDto> = emptyList(),
)

@Serializable
data class ConditionDto(
    val name: String,
    @SerialName("confidence_percent") val confidencePercent: Int = 0,
    val rationale: String = "",
)

@Serializable
data class RedFlagDto(
    val description: String,
    val action: String = "",
)

@Serializable
data class OtcAdviceDto(
    val medicine: String,
    val dosage: String = "",
    val note: String = "",
    val prescription: Boolean = false,
)

@Serializable
data class FeedbackRequestDto(
    val decision: String,
    val note: String? = null,
)

/** Static — the backend does not send one (decision D1 wording). */
internal const val ASSESSMENT_DISCLAIMER =
    "Preliminary triage support only — not a diagnosis or prescription. " +
        "The pharmacist remains the final decision-maker. Refer to a doctor when in doubt."

internal fun AssessmentDto.toDomain(): Assessment = Assessment(
    id = id,
    caseId = recordingId,
    symptomSummary = symptomSummary,
    conditions = conditions.map { ConditionHypothesis(it.name, it.confidencePercent, it.rationale) },
    redFlags = redFlags.map { RedFlag(it.description, it.action) },
    otcGuidance = otcGuidance.map { OtcAdvice(it.medicine, it.dosage, it.note, it.prescription) },
    disclaimer = ASSESSMENT_DISCLAIMER,
)

/** Backend `RecordingStatus` → the stage shown while the pipeline runs. */
internal fun pipelineStageFor(status: String): PipelineStage = when (status) {
    "diarizing" -> PipelineStage.DIARIZING
    "transcribing" -> PipelineStage.TRANSCRIBING
    "filtering", "extracting" -> PipelineStage.EXTRACTING
    "assessing" -> PipelineStage.ASSESSING
    else -> PipelineStage.UPLOADING // uploaded / queued: not yet picked up
}
