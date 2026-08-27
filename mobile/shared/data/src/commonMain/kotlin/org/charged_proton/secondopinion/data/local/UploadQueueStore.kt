package org.charged_proton.secondopinion.data.local

import kotlinx.coroutines.flow.Flow
import org.charged_proton.secondopinion.domain.model.PipelineStage

enum class UploadQueueState {
    ENQUEUED,
    UPLOADING,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
}

data class UploadQueueEntry(
    val caseId: String,
    val ownerId: String,
    val state: UploadQueueState,
    val pipelineStage: PipelineStage?,
    val lastError: String?,
    val attemptCount: Int,
)

interface UploadQueueStore {
    suspend fun enqueue(caseId: String): UploadQueueEntry
    fun observe(caseId: String): Flow<UploadQueueEntry?>
    suspend fun get(caseId: String): UploadQueueEntry?
    suspend fun update(
        caseId: String,
        state: UploadQueueState,
        stage: PipelineStage? = null,
        error: String? = null,
        attemptCount: Int,
    )
    suspend fun delete(caseId: String)
}