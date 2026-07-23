package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.testutil.FakeAssessmentRepository
import org.charged_proton.secondopinion.domain.testutil.testAssessment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssessmentUseCasesTest {

    private val repository = FakeAssessmentRepository()

    @Test
    fun requestAssessment_streamsRepositoryProgressForCase() = runTest {
        val assessment = testAssessment(caseId = "case-1")
        val progress = listOf(
            AssessmentProgress.InProgress(PipelineStage.UPLOADING),
            AssessmentProgress.Completed(assessment),
        )
        repository.progressFlow = flowOf(*progress.toTypedArray())

        val emitted = RequestAssessmentUseCase(repository)("case-1").toList()

        assertEquals(progress, emitted)
        assertEquals(listOf("case-1"), repository.requestedCaseIds)
    }

    @Test
    fun getAssessment_returnsAssessmentOrNull() = runTest {
        val assessment = testAssessment(caseId = "case-1")
        repository.assessmentsByCaseId["case-1"] = assessment
        val useCase = GetAssessmentUseCase(repository)

        assertEquals(assessment, useCase("case-1"))
        assertNull(useCase("case-2"))
    }

    @Test
    fun submitFeedback_success_storesDecision() = runTest {
        val feedback = Feedback("assessment-1", PharmacistDecision.ACCEPTED, note = "ok")

        val result = SubmitFeedbackUseCase(repository)(feedback)

        assertTrue(result.isSuccess)
        assertEquals(feedback, repository.feedbackByAssessmentId["assessment-1"])
    }

    @Test
    fun submitFeedback_repositoryFails_returnsFailure() = runTest {
        val boom = RuntimeException("network down")
        repository.submitError = boom

        val result =
            SubmitFeedbackUseCase(repository)(Feedback("assessment-1", PharmacistDecision.REJECTED))

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun getFeedback_returnsStoredDecisionOrNull() = runTest {
        val feedback = Feedback("assessment-1", PharmacistDecision.OVERRIDDEN)
        repository.feedbackByAssessmentId["assessment-1"] = feedback
        val useCase = GetFeedbackUseCase(repository)

        assertEquals(feedback, useCase("assessment-1"))
        assertNull(useCase("assessment-2"))
    }

    @Test
    fun deleteCase_success_deletesThroughRepository() = runTest {
        val result = DeleteCaseUseCase(repository)("case-1")

        assertTrue(result.isSuccess)
        assertEquals(listOf("case-1"), repository.deletedCaseIds)
    }

    @Test
    fun deleteCase_repositoryFails_returnsFailure() = runTest {
        val boom = RuntimeException("network down")
        repository.deleteError = boom

        val result = DeleteCaseUseCase(repository)("case-1")

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }
}
