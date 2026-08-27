package org.charged_proton.secondopinion.presentation.assessment

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase
import org.charged_proton.secondopinion.presentation.testutil.FakeAssessmentRepository
import org.charged_proton.secondopinion.presentation.testutil.testAssessment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentViewModelTest {

    private val repository = FakeAssessmentRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(caseId: String = "case-1") = AssessmentViewModel(
        caseId,
        RequestAssessmentUseCase(repository),
        GetFeedbackUseCase(repository),
        SubmitFeedbackUseCase(repository),
    )

    @Test
    fun init_requestsAssessmentForGivenCase() = runTest {
        viewModel("case-42")

        assertEquals(listOf("case-42"), repository.requestedCaseIds)
    }

    @Test
    fun progressStream_updatesStageThenShowsAssessment() = runTest {
        val assessment = testAssessment()
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(AssessmentUiState(), awaitItem())

            repository.progress.emit(AssessmentProgress.InProgress(PipelineStage.UPLOADING))
            assertEquals(PipelineStage.UPLOADING, awaitItem().stage)

            repository.progress.emit(AssessmentProgress.InProgress(PipelineStage.ASSESSING))
            assertEquals(PipelineStage.ASSESSING, awaitItem().stage)

            repository.progress.emit(AssessmentProgress.Completed(assessment))
            val state = awaitItem()
            assertNull(state.stage)
            assertEquals(assessment, state.assessment)
            assertNull(state.decision)
        }
    }

    @Test
    fun completed_loadsPreviouslyRecordedDecision() = runTest {
        val assessment = testAssessment()
        repository.feedbackByAssessmentId[assessment.id] =
            Feedback(assessment.id, PharmacistDecision.ACCEPTED)
        val vm = viewModel()

        repository.progress.emit(AssessmentProgress.Completed(assessment))

        assertEquals(PharmacistDecision.ACCEPTED, vm.uiState.value.decision)
    }

    @Test
    fun failedProgress_surfacesErrorMessage() = runTest {
        val vm = viewModel()

        repository.progress.emit(AssessmentProgress.Failed("Case not found"))

        assertEquals("Case not found", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.stage)
    }

    @Test
    fun queuedProgress_surfacesRetryMetadata() = runTest {
        val vm = viewModel()

        repository.progress.emit(AssessmentProgress.Queued(2, "connection refused"))

        val state = vm.uiState.value
        assertTrue(state.isQueued)
        assertEquals(2, state.queueAttemptCount)
        assertEquals("connection refused", state.lastQueueError)
    }

    @Test
    fun onRetry_startsNewAssessmentRequest() = runTest {
        val vm = viewModel("case-42")

        vm.onRetry()

        assertEquals(listOf("case-42", "case-42"), repository.requestedCaseIds)
    }

    @Test
    fun progressFlowThrows_surfacesErrorMessage() = runTest {
        repository.progressOverride = flow { throw IllegalStateException("pipeline crashed") }

        val vm = viewModel()

        assertEquals("pipeline crashed", vm.uiState.value.errorMessage)
    }

    @Test
    fun onDecision_success_recordsDecision() = runTest {
        val assessment = testAssessment()
        val vm = viewModel()
        repository.progress.emit(AssessmentProgress.Completed(assessment))

        vm.onDecision(PharmacistDecision.OVERRIDDEN, note = "different antibiotic")

        val state = vm.uiState.value
        assertEquals(PharmacistDecision.OVERRIDDEN, state.decision)
        assertFalse(state.isSubmittingDecision)
        assertEquals(
            Feedback(assessment.id, PharmacistDecision.OVERRIDDEN, "different antibiotic"),
            repository.feedbackByAssessmentId[assessment.id],
        )
    }

    @Test
    fun onDecision_failure_surfacesErrorAndKeepsDecisionUnset() = runTest {
        val vm = viewModel()
        repository.progress.emit(AssessmentProgress.Completed(testAssessment()))
        repository.submitError = RuntimeException("network down")

        vm.onDecision(PharmacistDecision.ACCEPTED)

        val state = vm.uiState.value
        assertEquals("network down", state.errorMessage)
        assertNull(state.decision)
        assertFalse(state.isSubmittingDecision)
    }

    @Test
    fun onDecision_withoutAssessment_isIgnored() = runTest {
        val vm = viewModel()

        vm.onDecision(PharmacistDecision.ACCEPTED)

        assertTrue(repository.feedbackByAssessmentId.isEmpty())
        assertNull(vm.uiState.value.decision)
    }
}
