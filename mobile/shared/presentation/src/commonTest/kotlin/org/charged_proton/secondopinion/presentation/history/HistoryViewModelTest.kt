package org.charged_proton.secondopinion.presentation.history

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.usecase.DeleteCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopPlaybackUseCase
import org.charged_proton.secondopinion.presentation.testutil.FakeAssessmentRepository
import org.charged_proton.secondopinion.presentation.testutil.FakeAudioPlayer
import org.charged_proton.secondopinion.presentation.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.presentation.testutil.testRecording
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val caseRepository = FakeCaseRepository()
    private val player = FakeAudioPlayer()
    private val assessmentRepository = FakeAssessmentRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HistoryViewModel(
        ObserveCasesUseCase(caseRepository),
        PlayRecordingUseCase(player),
        StopPlaybackUseCase(player),
        DeleteCaseUseCase(assessmentRepository),
    )

    @Test
    fun uiState_startsEmpty() = runTest {
        assertEquals(HistoryUiState(), viewModel().uiState.value)
    }

    @Test
    fun uiState_streamsCasesNewestFirst() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(HistoryUiState(), awaitItem())

            val old = caseRepository.createCase(testRecording(createdAtEpochMillis = 1L))
            assertEquals(HistoryUiState(listOf(old)), awaitItem())

            val recent = caseRepository.createCase(testRecording(createdAtEpochMillis = 2L))
            assertEquals(HistoryUiState(listOf(recent, old)), awaitItem())
        }
    }

    @Test
    fun togglePlayback_playsRecordingThenStopsOnSecondTap() = runTest {
        val case = caseRepository.createCase(testRecording(filePath = "/tmp/case.m4a"))
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // case list

            vm.onTogglePlayback(case)
            assertEquals(case.id, awaitItem().playingCaseId)
            assertEquals(listOf("/tmp/case.m4a"), player.playedFilePaths)

            vm.onTogglePlayback(case)
            assertNull(awaitItem().playingCaseId)
            assertEquals(2, player.stopCalls) // once before play, once on toggle-off
        }
    }

    @Test
    fun playbackCompletion_clearsPlayingCase() = runTest {
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()

            vm.onTogglePlayback(case)
            assertEquals(case.id, awaitItem().playingCaseId)

            player.completePlayback()
            assertNull(awaitItem().playingCaseId)
        }
    }

    @Test
    fun togglePlayback_playerThrows_staysStopped() = runTest {
        player.playError = IllegalStateException("bad file")
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()

            vm.onTogglePlayback(case)

            expectNoEvents()
            assertNull(vm.uiState.value.playingCaseId)
        }
    }

    @Test
    fun deleteRequested_asksForConfirmation() = runTest {
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()

            vm.onDeleteRequested(case)

            assertEquals(case.id, awaitItem().confirmingDeleteCaseId)
            assertTrue(assessmentRepository.deletedCaseIds.isEmpty())
        }
    }

    @Test
    fun deleteDismissed_closesConfirmationWithoutDeleting() = runTest {
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.onDeleteRequested(case)
            awaitItem()

            vm.onDeleteDismissed()

            assertNull(awaitItem().confirmingDeleteCaseId)
            assertTrue(assessmentRepository.deletedCaseIds.isEmpty())
        }
    }

    @Test
    fun deleteConfirmed_deletesCaseAndClosesConfirmation() = runTest {
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.onDeleteRequested(case)
            awaitItem()

            vm.onDeleteConfirmed()

            assertNull(awaitItem().confirmingDeleteCaseId)
            assertEquals(listOf(case.id), assessmentRepository.deletedCaseIds)
        }
    }

    @Test
    fun deleteConfirmed_whileCasePlaying_stopsPlaybackFirst() = runTest {
        val case = caseRepository.createCase(testRecording())
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.onTogglePlayback(case)
            assertEquals(case.id, awaitItem().playingCaseId)
            vm.onDeleteRequested(case)
            awaitItem()

            vm.onDeleteConfirmed()

            val state = expectMostRecentItem()
            assertNull(state.playingCaseId)
            assertNull(state.confirmingDeleteCaseId)
            assertEquals(2, player.stopCalls) // once before play, once on delete
            assertEquals(listOf(case.id), assessmentRepository.deletedCaseIds)
        }
    }
}
