package org.charged_proton.secondopinion.domain.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

class FakeAudioRecorder : AudioRecorder {
    var startError: Throwable? = null
    var stopError: Throwable? = null
    var stopResult: Recording? = null
    var startCalls = 0
    var stopCalls = 0
    var releaseCalls = 0

    override var isRecording: Boolean = false
        private set

    override fun start() {
        startCalls++
        startError?.let { throw it }
        isRecording = true
    }

    override suspend fun stop(): Recording? {
        stopCalls++
        stopError?.let { throw it }
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
    var createError: Throwable? = null
    var nextId = 1

    override fun observeCases(): Flow<List<SymptomCase>> =
        cases.map { list -> list.sortedByDescending(SymptomCase::createdAtEpochMillis) }

    override suspend fun createCase(recording: Recording): SymptomCase {
        createError?.let { throw it }
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

class FakeAssessmentRepository : AssessmentRepository {
    var progressFlow: Flow<AssessmentProgress> = emptyFlow()
    var requestedCaseIds = mutableListOf<String>()
    val assessmentsByCaseId = mutableMapOf<String, Assessment>()
    val feedbackByAssessmentId = mutableMapOf<String, Feedback>()
    var submitError: Throwable? = null

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> {
        requestedCaseIds += caseId
        return progressFlow
    }

    override suspend fun getAssessment(caseId: String): Assessment? =
        assessmentsByCaseId[caseId]

    override suspend fun submitFeedback(feedback: Feedback): Result<Unit> {
        submitError?.let { return Result.failure(it) }
        feedbackByAssessmentId[feedback.assessmentId] = feedback
        return Result.success(Unit)
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? =
        feedbackByAssessmentId[assessmentId]
}
