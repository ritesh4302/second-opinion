package org.charged_proton.secondopinion.data.queue

import kotlinx.coroutines.flow.collect
import org.charged_proton.secondopinion.data.local.UploadQueueState
import org.charged_proton.secondopinion.data.local.UploadQueueStore
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

sealed interface QueueProcessResult {
    data object Success : QueueProcessResult
    data class Retry(val reason: String) : QueueProcessResult
    data class PermanentFailure(val reason: String) : QueueProcessResult
}

class UploadQueueProcessor(
    private val backend: AssessmentRepository,
    private val queueStore: UploadQueueStore,
    private val caseRepository: CaseRepository,
) {
    suspend fun process(caseId: String, attemptCount: Int): QueueProcessResult {
        var result: QueueProcessResult = QueueProcessResult.Retry("Upload did not complete")
        backend.requestAssessment(caseId).collect { progress ->
            when (progress) {
                is AssessmentProgress.Queued -> Unit
                is AssessmentProgress.InProgress -> {
                    val state = if (progress.stage == PipelineStage.UPLOADING) {
                        UploadQueueState.UPLOADING
                    } else {
                        UploadQueueState.PROCESSING
                    }
                    queueStore.update(caseId, state, progress.stage, attemptCount = attemptCount)
                }
                is AssessmentProgress.Completed -> {
                    queueStore.update(
                        caseId,
                        UploadQueueState.COMPLETED,
                        attemptCount = attemptCount,
                    )
                    result = QueueProcessResult.Success
                }
                is AssessmentProgress.Failed -> {
                    if (progress.retryable) {
                        queueStore.update(
                            caseId,
                            UploadQueueState.RETRY_WAIT,
                            error = progress.reason,
                            attemptCount = attemptCount,
                        )
                        caseRepository.updateStatus(caseId, CaseStatus.RETRYING)
                        result = QueueProcessResult.Retry(progress.reason)
                    } else {
                        queueStore.update(
                            caseId,
                            UploadQueueState.FAILED,
                            error = progress.reason,
                            attemptCount = attemptCount,
                        )
                        result = QueueProcessResult.PermanentFailure(progress.reason)
                    }
                }
            }
        }
        return result
    }

    suspend fun markRetriesExhausted(caseId: String, reason: String, attemptCount: Int) {
        queueStore.update(
            caseId,
            UploadQueueState.FAILED,
            error = reason,
            attemptCount = attemptCount,
        )
        caseRepository.updateStatus(caseId, CaseStatus.FAILED)
    }

    suspend fun markRetryScheduled(caseId: String, reason: String, attemptCount: Int) {
        queueStore.update(
            caseId,
            UploadQueueState.RETRY_WAIT,
            error = reason,
            attemptCount = attemptCount,
        )
        caseRepository.updateStatus(caseId, CaseStatus.RETRYING)
    }
}