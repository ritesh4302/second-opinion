package org.charged_proton.secondopinion.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.CaseRepository

class FakeAudioRecorder : AudioRecorder {
    var stopResult: Recording? = Recording("/tmp/test.m4a", 1_000L)
    var releaseCalls = 0

    override var isRecording: Boolean = false
        private set

    override fun start() {
        isRecording = true
    }

    override suspend fun stop(): Recording? {
        isRecording = false
        return stopResult
    }

    override fun release() {
        releaseCalls++
        isRecording = false
    }
}

class FakeCaseRepository : CaseRepository {
    val cases = MutableStateFlow<List<SymptomCase>>(emptyList())
    private var nextId = 1

    override fun observeCases(): Flow<List<SymptomCase>> = cases

    override suspend fun createCase(recording: Recording): SymptomCase {
        val case = SymptomCase(
            id = "case-${nextId++}",
            recording = recording,
            status = CaseStatus.RECORDED,
            createdAtEpochMillis = recording.createdAtEpochMillis,
        )
        cases.update { it + case }
        return case
    }

    override suspend fun getCase(caseId: String): SymptomCase? =
        cases.value.firstOrNull { it.id == caseId }

    override suspend fun updateStatus(caseId: String, status: CaseStatus) {
        cases.update { list ->
            list.map { if (it.id == caseId) it.copy(status = status) else it }
        }
    }
}
