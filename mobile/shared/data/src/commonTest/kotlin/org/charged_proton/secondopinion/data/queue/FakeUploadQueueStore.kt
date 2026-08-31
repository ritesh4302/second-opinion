package org.charged_proton.secondopinion.data.queue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.charged_proton.secondopinion.data.local.UploadQueueEntry
import org.charged_proton.secondopinion.data.local.UploadQueueState
import org.charged_proton.secondopinion.data.local.UploadQueueStore
import org.charged_proton.secondopinion.domain.model.PipelineStage

internal class FakeUploadQueueStore : UploadQueueStore {
    private val entries = mutableMapOf<String, MutableStateFlow<UploadQueueEntry?>>()

    override suspend fun enqueue(caseId: String): UploadQueueEntry {
        val flow = entries.getOrPut(caseId) { MutableStateFlow(null) }
        val existing = flow.value
        val entry = if (existing == null || existing.state == UploadQueueState.FAILED) {
            UploadQueueEntry(caseId, OWNER_ID, UploadQueueState.ENQUEUED, null, null, 0)
        } else {
            existing
        }
        flow.value = entry
        return entry
    }

    override fun observe(caseId: String): Flow<UploadQueueEntry?> =
        entries.getOrPut(caseId) { MutableStateFlow(null) }

    override suspend fun get(caseId: String): UploadQueueEntry? = entries[caseId]?.value

    override suspend fun pending(): List<UploadQueueEntry> =
        entries.values.mapNotNull { it.value }.filter {
            it.state != UploadQueueState.COMPLETED && it.state != UploadQueueState.FAILED
        }

    override suspend fun update(
        caseId: String,
        state: UploadQueueState,
        stage: PipelineStage?,
        error: String?,
        attemptCount: Int,
    ) {
        val current = get(caseId) ?: enqueue(caseId)
        entries.getValue(caseId).value = current.copy(
            state = state,
            pipelineStage = stage,
            lastError = error,
            attemptCount = attemptCount,
        )
    }

    override suspend fun delete(caseId: String) {
        entries.remove(caseId)?.value = null
    }

    private companion object {
        const val OWNER_ID = "owner-1"
    }
}