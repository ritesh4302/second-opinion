package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.domain.testutil.testRecording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CaseUseCasesTest {

    private val repository = FakeCaseRepository()

    @Test
    fun createCase_success_returnsCaseFromRepository() = runTest {
        val recording = testRecording()

        val result = CreateCaseUseCase(repository)(recording)

        assertTrue(result.isSuccess)
        val case = result.getOrThrow()
        assertEquals(recording, case.recording)
        assertEquals(CaseStatus.RECORDED, case.status)
        assertEquals(case, repository.getCase(case.id))
    }

    @Test
    fun createCase_repositoryThrows_wrapsInFailure() = runTest {
        val boom = IllegalStateException("disk full")
        repository.createError = boom

        val result = CreateCaseUseCase(repository)(testRecording())

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun observeCases_streamsRepositoryCasesNewestFirst() = runTest {
        val useCase = ObserveCasesUseCase(repository)
        assertEquals(emptyList(), useCase().first())

        val old = CreateCaseUseCase(repository)(testRecording(createdAtEpochMillis = 1L)).getOrThrow()
        val recent = CreateCaseUseCase(repository)(testRecording(createdAtEpochMillis = 2L)).getOrThrow()

        assertEquals(listOf(recent, old), useCase().first())
    }

    @Test
    fun getCase_returnsMatchingCaseOrNull() = runTest {
        val case = CreateCaseUseCase(repository)(testRecording()).getOrThrow()
        val useCase = GetCaseUseCase(repository)

        assertEquals(case, useCase(case.id))
        assertNull(useCase("missing-id"))
    }
}
