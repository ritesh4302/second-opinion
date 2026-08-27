package org.charged_proton.secondopinion.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import org.charged_proton.secondopinion.data.local.AssessmentStore
import org.charged_proton.secondopinion.data.local.UploadQueueState
import org.charged_proton.secondopinion.data.local.UploadQueueStore
import org.charged_proton.secondopinion.data.queue.AssessmentWorkScheduler
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/** Queues backend assessment work and exposes durable progress to the UI. */
class QueuedAssessmentRepository(
    private val backend: AssessmentRepository,
    private val assessmentStore: AssessmentStore,
    private val caseRepository: CaseRepository,
    private val queueStore: UploadQueueStore,
    private val scheduler: AssessmentWorkScheduler,
) : AssessmentRepository {

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> = flow {
        assessmentStore.getAssessment(caseId)?.let {
            emit(AssessmentProgress.Completed(it))
            return@flow
        }
        if (caseRepository.getCase(caseId) == null) {
            emit(AssessmentProgress.Failed("Case not found: $caseId"))
            return@flow
        }

        val queued = queueStore.enqueue(caseId)
        if (queued.state == UploadQueueState.ENQUEUED) {
            caseRepository.updateStatus(caseId, CaseStatus.QUEUED)
        }
        scheduler.enqueue(caseId, queued.ownerId)

        queueStore.observe(caseId).transformWhile { entry ->
            if (entry == null) return@transformWhile true
            val progress = when (entry.state) {
                UploadQueueState.ENQUEUED,
                UploadQueueState.RETRY_WAIT,
                -> AssessmentProgress.Queued(entry.attemptCount, entry.lastError)
                UploadQueueState.UPLOADING ->
                    AssessmentProgress.InProgress(PipelineStage.UPLOADING)
                UploadQueueState.PROCESSING ->
                    AssessmentProgress.InProgress(entry.pipelineStage ?: PipelineStage.UPLOADING)
                UploadQueueState.COMPLETED -> assessmentStore.getAssessment(caseId)?.let {
                    AssessmentProgress.Completed(it)
                } ?: AssessmentProgress.Failed("Completed assessment is unavailable", retryable = true)
                UploadQueueState.FAILED ->
                    AssessmentProgress.Failed(entry.lastError ?: "Assessment failed")
            }
            emit(progress)
            entry.state != UploadQueueState.COMPLETED && entry.state != UploadQueueState.FAILED
        }.collect(::emit)
    }

    override suspend fun getAssessment(caseId: String): Assessment? = backend.getAssessment(caseId)

    override suspend fun submitFeedback(feedback: Feedback): Result<Unit> =
        backend.submitFeedback(feedback)

    override suspend fun getFeedback(assessmentId: String): Feedback? =
        backend.getFeedback(assessmentId)

    override suspend fun deleteCase(caseId: String): Result<Unit> {
        val queued = queueStore.get(caseId)
        queued?.let { scheduler.cancel(caseId, it.ownerId) }
        val result = backend.deleteCase(caseId)
        if (result.isSuccess) queueStore.delete(caseId)
        return result
    }
}