package org.charged_proton.secondopinion.domain.model

/** Progress of a case through the assessment pipeline (BACKEND.md §2.2 state machine). */
sealed interface AssessmentProgress {
    data class InProgress(val stage: PipelineStage) : AssessmentProgress
    data class Completed(val assessment: Assessment) : AssessmentProgress
    data class Failed(val reason: String) : AssessmentProgress
}

enum class PipelineStage {
    UPLOADING,
    DIARIZING,
    TRANSCRIBING,
    EXTRACTING,
    ASSESSING,
}
