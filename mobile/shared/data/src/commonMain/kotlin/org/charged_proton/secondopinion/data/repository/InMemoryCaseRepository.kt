package org.charged_proton.secondopinion.data.repository

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/**
 * Mock: in-memory case store. Will be replaced by a SQLDelight-backed
 * implementation when persistence lands (ANDROID_APP.md §10 step 4).
 *
 * Case ids are UUIDs: the case id doubles as the backend recording id
 * (the client-generated idempotency key of POST /v1/recordings).
 */
class InMemoryCaseRepository : CaseRepository {

    private val cases = MutableStateFlow<Map<String, SymptomCase>>(emptyMap())

    override fun observeCases(): Flow<List<SymptomCase>> =
        cases.map { byId ->
            byId.values.sortedByDescending(SymptomCase::createdAtEpochMillis)
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createCase(recording: Recording): SymptomCase {
        val case = SymptomCase(
            id = Uuid.random().toString(),
            recording = recording,
            status = CaseStatus.RECORDED,
            createdAtEpochMillis = recording.createdAtEpochMillis,
        )
        cases.update { it + (case.id to case) }
        return case
    }

    override suspend fun getCase(caseId: String): SymptomCase? = cases.value[caseId]

    override suspend fun updateStatus(caseId: String, status: CaseStatus) {
        cases.update { byId ->
            val case = byId[caseId] ?: return@update byId
            byId + (caseId to case.copy(status = status))
        }
    }

    override suspend fun deleteCase(caseId: String) {
        cases.update { it - caseId }
    }
}
