package org.charged_proton.secondopinion.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class InMemoryCaseRepositoryTest {

    private val repository = InMemoryCaseRepository()

    private fun recording(at: Long) = Recording("/tmp/rec-$at.m4a", at)

    @Test
    fun createCase_startsInRecordedStatusWithUniqueIds() = runTest {
        val first = repository.createCase(recording(1L))
        val second = repository.createCase(recording(1L))

        assertEquals(CaseStatus.RECORDED, first.status)
        assertEquals(CaseStatus.RECORDED, second.status)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun observeCases_emitsNewestFirstOnEveryChange() = runTest {
        repository.observeCases().test {
            assertEquals(emptyList(), awaitItem())

            val old = repository.createCase(recording(1L))
            assertEquals(listOf(old), awaitItem())

            val recent = repository.createCase(recording(2L))
            assertEquals(listOf(recent, old), awaitItem())
        }
    }

    @Test
    fun getCase_returnsStoredCaseOrNull() = runTest {
        val case = repository.createCase(recording(1L))

        assertEquals(case, repository.getCase(case.id))
        assertNull(repository.getCase("missing"))
    }

    @Test
    fun updateStatus_changesOnlyTargetCase() = runTest {
        val a = repository.createCase(recording(1L))
        val b = repository.createCase(recording(2L))

        repository.updateStatus(a.id, CaseStatus.COMPLETED)

        assertEquals(CaseStatus.COMPLETED, repository.getCase(a.id)?.status)
        assertEquals(CaseStatus.RECORDED, repository.getCase(b.id)?.status)
    }

    @Test
    fun updateStatus_unknownCase_isNoOp() = runTest {
        val case = repository.createCase(recording(1L))

        repository.updateStatus("missing", CaseStatus.FAILED)

        assertEquals(listOf(case), repository.observeCases().first())
    }

    @Test
    fun deleteCase_removesOnlyTargetCase() = runTest {
        val a = repository.createCase(recording(1L))
        val b = repository.createCase(recording(2L))

        repository.deleteCase(a.id)

        assertNull(repository.getCase(a.id))
        assertEquals(listOf(b), repository.observeCases().first())
    }
}
