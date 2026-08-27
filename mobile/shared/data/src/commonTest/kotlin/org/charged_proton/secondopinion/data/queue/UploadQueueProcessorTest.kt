package org.charged_proton.secondopinion.data.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.local.UploadQueueState
import org.charged_proton.secondopinion.data.repository.InMemoryCaseRepository
import org.charged_proton.secondopinion.domain.model.Assessment
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.repository.AssessmentRepository

class UploadQueueProcessorTest {
    private val cases = InMemoryCaseRepository()
    private val queue = FakeUploadQueueStore()

    @Test
    fun completedPipelineMarksQueueCompleted() = runTest {
        val caseId = seededCase()
        val assessment = Assessment("a-1", caseId, "summary", emptyList(), emptyList(), emptyList(), "notice")
        val processor = processor(
            flowOf(
                AssessmentProgress.InProgress(PipelineStage.UPLOADING),
                AssessmentProgress.Completed(assessment),
            ),
        )

        val result = processor.process(caseId, attemptCount = 1)

        assertIs<QueueProcessResult.Success>(result)
        assertEquals(UploadQueueState.COMPLETED, queue.get(caseId)?.state)
    }

    @Test
    fun retryableFailureWaitsForRetryAndKeepsAttemptCount() = runTest {
        val caseId = seededCase()
        val processor = processor(flowOf(AssessmentProgress.Failed("offline", retryable = true)))

        val result = processor.process(caseId, attemptCount = 2)

        assertIs<QueueProcessResult.Retry>(result)
        assertEquals(UploadQueueState.RETRY_WAIT, queue.get(caseId)?.state)
        assertEquals(2, queue.get(caseId)?.attemptCount)
        assertEquals(CaseStatus.RETRYING, cases.getCase(caseId)?.status)
    }

    @Test
    fun permanentFailureDoesNotRetry() = runTest {
        val caseId = seededCase()
        val result = processor(flowOf(AssessmentProgress.Failed("invalid audio")))
            .process(caseId, attemptCount = 1)

        assertIs<QueueProcessResult.PermanentFailure>(result)
        assertEquals(UploadQueueState.FAILED, queue.get(caseId)?.state)
    }

    private suspend fun seededCase(): String {
        val caseId = cases.createCase(Recording("/private/audio.m4a", 1)).id
        queue.enqueue(caseId)
        return caseId
    }

    private fun processor(progress: Flow<AssessmentProgress>) =
        UploadQueueProcessor(FakeRepository(progress), queue, cases)

    private class FakeRepository(
        private val progress: Flow<AssessmentProgress>,
    ) : AssessmentRepository {
        override fun requestAssessment(caseId: String) = progress
        override suspend fun getAssessment(caseId: String): Assessment? = null
        override suspend fun submitFeedback(feedback: Feedback) = Result.success(Unit)
        override suspend fun getFeedback(assessmentId: String): Feedback? = null
        override suspend fun deleteCase(caseId: String) = Result.success(Unit)
    }
}