package org.charged_proton.secondopinion.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.charged_proton.secondopinion.data.mock.MockAssessmentScenarios
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository
import org.charged_proton.secondopinion.domain.repository.CaseRepository

/**
 * Mock: simulates the backend pipeline (BACKEND.md §2.2) with staged delays
 * and canned assessments. Will be replaced by a Ktor-backed implementation
 * (upload + poll) when the backend exists.
 */
class MockAssessmentRepository(
    private val caseRepository: CaseRepository,
    private val stageDelayMillis: Long = 700,
) : AssessmentRepository {

    private val mutex = Mutex()
    private val assessmentsByCaseId = mutableMapOf<String, Assessment>()
    private val feedbackByAssessmentId = mutableMapOf<String, Feedback>()
    private var submissionCount = 0

    override fun requestAssessment(caseId: String): Flow<AssessmentProgress> = flow {
        getAssessment(caseId)?.let {
            emit(AssessmentProgress.Completed(it))
            return@flow
        }
        if (caseRepository.getCase(caseId) == null) {
            emit(AssessmentProgress.Failed("Case not found: $caseId"))
            return@flow
        }

        caseRepository.updateStatus(caseId, CaseStatus.UPLOADING)
        emit(AssessmentProgress.InProgress(PipelineStage.UPLOADING))
        delay(stageDelayMillis)

        caseRepository.updateStatus(caseId, CaseStatus.PROCESSING)
        for (stage in listOf(
            PipelineStage.DIARIZING,
            PipelineStage.TRANSCRIBING,
            PipelineStage.EXTRACTING,
            PipelineStage.ASSESSING,
        )) {
            emit(AssessmentProgress.InProgress(stage))
            delay(stageDelayMillis)
        }

        val assessment = mutex.withLock {
            val scenario =
                MockAssessmentScenarios.scenarios[submissionCount % MockAssessmentScenarios.scenarios.size]
            submissionCount++
            scenario("assessment-$caseId", caseId).also { assessmentsByCaseId[caseId] = it }
        }
        caseRepository.updateStatus(caseId, CaseStatus.COMPLETED)
        emit(AssessmentProgress.Completed(assessment))
    }

    override suspend fun getAssessment(caseId: String): Assessment? =
        mutex.withLock { assessmentsByCaseId[caseId] }

    override suspend fun submitFeedback(feedback: Feedback): Result<Unit> = runCatching {
        delay(300)
        mutex.withLock { feedbackByAssessmentId[feedback.assessmentId] = feedback }
    }

    override suspend fun getFeedback(assessmentId: String): Feedback? =
        mutex.withLock { feedbackByAssessmentId[assessmentId] }
}
