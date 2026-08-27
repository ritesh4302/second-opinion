package org.charged_proton.secondopinion.domain.model

/** Progress of a case through the assessment pipeline (BACKEND.md §2.2 state machine). */
sealed interface AssessmentProgress {
    data class Queued(val attemptCount: Int = 0, val lastError: String? = null) : AssessmentProgress
    data class InProgress(val stage: PipelineStage) : AssessmentProgress
    data class Completed(val assessment: Assessment) : AssessmentProgress
    data class Failed(val reason: String, val retryable: Boolean = false) : AssessmentProgress
}

enum class PipelineStage {
    UPLOADING,
    DIARIZING,
    TRANSCRIBING,
    EXTRACTING,
    ASSESSING,
}
