package org.charged_proton.secondopinion.domain.repository

import kotlinx.coroutines.flow.Flow
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase

/** Port for storing and observing symptom cases. */
interface CaseRepository {

    /** All cases, newest first; emits on every change. */
    fun observeCases(): Flow<List<SymptomCase>>

    suspend fun createCase(recording: Recording): SymptomCase

    suspend fun getCase(caseId: String): SymptomCase?

    suspend fun updateStatus(caseId: String, status: CaseStatus)
}
