package org.charged_proton.secondopinion.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.PipelineStage

class SqlDelightUploadQueueStore(
    private val database: SecondOpinionDatabase,
    private val currentOwnerId: () -> String?,
) : UploadQueueStore {

    override suspend fun enqueue(caseId: String): UploadQueueEntry {
        val ownerId = requireOwnerId()
        return withContext(Dispatchers.Default) {
            database.uploadQueueQueries.transaction {
                database.uploadQueueQueries.insertQueue(caseId, ownerId)
                database.uploadQueueQueries.resetFailedQueue(caseId, ownerId)
            }
            checkNotNull(query(caseId, ownerId))
        }
    }

    override fun observe(caseId: String): Flow<UploadQueueEntry?> {
        val ownerId = currentOwnerId() ?: return flowOf(null)
        return database.uploadQueueQueries.selectQueue(caseId, ownerId, mapper = ::toEntry)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
    }

    override suspend fun get(caseId: String): UploadQueueEntry? {
        val ownerId = currentOwnerId() ?: return null
        return withContext(Dispatchers.Default) { query(caseId, ownerId) }
    }

    override suspend fun pending(): List<UploadQueueEntry> {
        val ownerId = currentOwnerId() ?: return emptyList()
        return withContext(Dispatchers.Default) {
            database.uploadQueueQueries
                .selectPendingByOwner(ownerId, mapper = ::toEntry)
                .executeAsList()
        }
    }

    override suspend fun update(
        caseId: String,
        state: UploadQueueState,
        stage: PipelineStage?,
        error: String?,
        attemptCount: Int,
    ) {
        val ownerId = currentOwnerId() ?: return
        withContext(Dispatchers.Default) {
            database.uploadQueueQueries.updateQueue(
                state = state.name,
                pipeline_stage = stage?.name,
                last_error = error,
                attempt_count = attemptCount.toLong(),
                case_id = caseId,
                owner_id = ownerId,
            )
        }
    }

    override suspend fun delete(caseId: String) {
        val ownerId = currentOwnerId() ?: return
        withContext(Dispatchers.Default) {
            database.uploadQueueQueries.deleteQueue(caseId, ownerId)
        }
    }

    private fun query(caseId: String, ownerId: String): UploadQueueEntry? =
        database.uploadQueueQueries.selectQueue(caseId, ownerId, mapper = ::toEntry)
            .executeAsOneOrNull()

    private fun toEntry(
        caseId: String,
        ownerId: String,
        state: String,
        pipelineStage: String?,
        lastError: String?,
        attemptCount: Long,
    ) = UploadQueueEntry(
        caseId = caseId,
        ownerId = ownerId,
        state = UploadQueueState.valueOf(state),
        pipelineStage = pipelineStage?.let(PipelineStage::valueOf),
        lastError = lastError,
        attemptCount = attemptCount.toInt(),
    )

    private fun requireOwnerId(): String =
        checkNotNull(currentOwnerId()) { "A signed-in owner is required to queue an upload" }
}