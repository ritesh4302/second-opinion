package org.charged_proton.secondopinion.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/** Persistent, owner-scoped case history backed by SQLDelight. */
class SqlDelightCaseRepository(
    private val database: SecondOpinionDatabase,
    private val currentOwnerId: () -> String?,
) : CaseRepository {

    override fun observeCases(): Flow<List<SymptomCase>> {
        val ownerId = currentOwnerId() ?: return flowOf(emptyList())
        return database.caseQueries
            .selectAllByOwner(ownerId, mapper = ::toCase)
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createCase(recording: Recording): SymptomCase {
        val ownerId = requireOwnerId()
        val case = SymptomCase(
            id = Uuid.random().toString(),
            recording = recording,
            status = CaseStatus.RECORDED,
            createdAtEpochMillis = recording.createdAtEpochMillis,
        )
        withContext(Dispatchers.Default) {
            database.caseQueries.insertCase(
                id = case.id,
                owner_id = ownerId,
                recording_path = recording.filePath,
                recording_created_at = recording.createdAtEpochMillis,
                duration_ms = recording.durationMillis,
                consent_confirmed = if (recording.consentConfirmed) 1 else 0,
                status = case.status.name,
                created_at = case.createdAtEpochMillis,
            )
        }
        return case
    }

    override suspend fun getCase(caseId: String): SymptomCase? {
        val ownerId = currentOwnerId() ?: return null
        return withContext(Dispatchers.Default) {
            database.caseQueries.selectById(caseId, ownerId, mapper = ::toCase).executeAsOneOrNull()
        }
    }

    override suspend fun updateStatus(caseId: String, status: CaseStatus) {
        val ownerId = currentOwnerId() ?: return
        withContext(Dispatchers.Default) {
            database.caseQueries.updateStatus(status.name, caseId, ownerId)
        }
    }

    override suspend fun deleteCase(caseId: String) {
        val ownerId = currentOwnerId() ?: return
        withContext(Dispatchers.Default) {
            database.caseQueries.deleteById(caseId, ownerId)
        }
    }

    private fun requireOwnerId(): String =
        checkNotNull(currentOwnerId()) { "A signed-in owner is required to create a case" }

    private fun toCase(
        id: String,
        _ownerId: String,
        recordingPath: String,
        recordingCreatedAt: Long,
        durationMillis: Long,
        consentConfirmed: Long,
        status: String,
        createdAt: Long,
    ) = SymptomCase(
        id = id,
        recording = Recording(
            filePath = recordingPath,
            createdAtEpochMillis = recordingCreatedAt,
            durationMillis = durationMillis,
            consentConfirmed = consentConfirmed != 0L,
        ),
        status = CaseStatus.valueOf(status),
        createdAtEpochMillis = createdAt,
    )
}