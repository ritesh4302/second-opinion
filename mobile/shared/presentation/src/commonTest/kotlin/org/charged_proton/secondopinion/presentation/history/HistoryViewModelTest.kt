package org.charged_proton.secondopinion.presentation.history

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.presentation.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.presentation.testutil.testRecording
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val caseRepository = FakeCaseRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HistoryViewModel(ObserveCasesUseCase(caseRepository))

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
}
