package org.charged_proton.secondopinion.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.local.UploadQueueState
import org.charged_proton.secondopinion.data.queue.AssessmentWorkScheduler
import org.charged_proton.secondopinion.data.queue.FakeUploadQueueStore
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

class QueuedAssessmentRepositoryTest {
    private val cases = InMemoryCaseRepository()
    private val queue = FakeUploadQueueStore()
    private val scheduler = FakeScheduler()
    private val backend = FakeBackendRepository()
    private val assessmentStore = FakeAssessmentStore()
    private val repository = QueuedAssessmentRepository(
        backend,
        assessmentStore,
        cases,
        queue,
        scheduler,
    )

    @Test
    fun requestQueuesCaseAndSchedulesNetworkWork() = runTest {
        val caseId = seededCase()

        val first = repository.requestAssessment(caseId).first()

        assertIs<AssessmentProgress.Queued>(first)
        assertEquals(listOf(caseId), scheduler.enqueuedCaseIds)
    }

    @Test
    fun requestingFailedCaseResetsQueueForManualRetry() = runTest {
        val caseId = seededCase()
        queue.enqueue(caseId)
        queue.update(caseId, UploadQueueState.FAILED, error = "offline", attemptCount = 5)

        val progress = repository.requestAssessment(caseId).first()

        assertIs<AssessmentProgress.Queued>(progress)
        assertEquals(0, progress.attemptCount)
    }

    @Test
    fun cachedAssessmentCompletesWithoutScheduling() = runTest {
        val caseId = seededCase()
        backend.assessment = Assessment(
            "a-1",
            caseId,
            "summary",
            emptyList(),
            emptyList(),
            emptyList(),
            "notice",
        )
        assessmentStore.saveAssessment(backend.assessment!!)

        val progress = repository.requestAssessment(caseId).first()

        assertIs<AssessmentProgress.Completed>(progress)
        assertTrue(scheduler.enqueuedCaseIds.isEmpty())
    }

    private suspend fun seededCase(): String =
        cases.createCase(Recording("/private/audio.m4a", 1)).id

    private class FakeScheduler : AssessmentWorkScheduler {
        val enqueuedCaseIds = mutableListOf<String>()
        override fun enqueue(caseId: String, ownerId: String) {
            enqueuedCaseIds += caseId
        }
        override fun cancel(caseId: String, ownerId: String) = Unit
    }

    private class FakeBackendRepository : AssessmentRepository {
        var assessment: Assessment? = null
        override fun requestAssessment(caseId: String): Flow<AssessmentProgress> = emptyFlow()
        override suspend fun getAssessment(caseId: String): Assessment? = assessment
        override suspend fun submitFeedback(feedback: Feedback) = Result.success(Unit)
        override suspend fun getFeedback(assessmentId: String): Feedback? = null
        override suspend fun deleteCase(caseId: String) = Result.success(Unit)
    }
}