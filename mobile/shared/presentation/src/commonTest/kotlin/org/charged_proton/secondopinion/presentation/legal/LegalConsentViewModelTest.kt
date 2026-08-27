package org.charged_proton.secondopinion.presentation.legal

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.legal.CURRENT_LEGAL_VERSION
import org.charged_proton.secondopinion.domain.legal.LegalAcceptance
import org.charged_proton.secondopinion.domain.legal.LegalConsentRepository
import org.charged_proton.secondopinion.domain.usecase.AcceptLegalTermsUseCase
import org.charged_proton.secondopinion.domain.usecase.GetLegalAcceptanceUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class LegalConsentViewModelTest {
    private val repository = FakeLegalRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun oldVersionRequiresFreshAcceptance() {
        repository.acceptance = LegalAcceptance("old-version", 1)

        assertEquals(false, viewModel().uiState.value.isAccepted)
    }

    @Test
    fun acceptPersistsCurrentVersionAndUnlocksGate() {
        val viewModel = viewModel()

        viewModel.onAccept()

        assertEquals(CURRENT_LEGAL_VERSION, repository.acceptance?.version)
        assertEquals(true, viewModel.uiState.value.isAccepted)
    }

    @Test
    fun persistenceFailureKeepsGateLocked() {
        repository.failure = IllegalStateException("disk unavailable")
        val viewModel = viewModel()

        viewModel.onAccept()

        assertEquals(false, viewModel.uiState.value.isAccepted)
        assertEquals(true, viewModel.uiState.value.acceptanceFailed)
    }

    private fun viewModel() = LegalConsentViewModel(
        "user-1",
        GetLegalAcceptanceUseCase(repository),
        AcceptLegalTermsUseCase(repository),
    )

    private class FakeLegalRepository : LegalConsentRepository {
        var acceptance: LegalAcceptance? = null
        var failure: Throwable? = null
        override fun getAcceptance(userId: String) = acceptance
        override suspend fun accept(userId: String, version: String): LegalAcceptance {
            failure?.let { throw it }
            return LegalAcceptance(version, 10).also { acceptance = it }
        }
    }
}