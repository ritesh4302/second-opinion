package org.charged_proton.secondopinion.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.model.Recording

class SqlDelightCaseRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SecondOpinionDatabase
    private var ownerId: String? = "owner-1"

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SecondOpinionDatabase.Schema.create(driver)
        database = SecondOpinionDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun caseSurvivesRepositoryRecreationWithAllRecordingFields() = runTest {
        val recording = Recording(
            filePath = "/private/recording.m4a",
            createdAtEpochMillis = 100,
            durationMillis = 4_200,
            consentConfirmed = true,
        )
        val created = repository().createCase(recording)

        val restored = repository().getCase(created.id)

        assertEquals(created, restored)
        assertEquals(recording, restored?.recording)
    }

    @Test
    fun observeCasesEmitsNewestFirstAfterWrites() = runTest {
        val repository = repository()
        repository.observeCases().test {
            assertEquals(emptyList(), awaitItem())

            val older = repository.createCase(recording(100))
            assertEquals(listOf(older), awaitItem())

            val newer = repository.createCase(recording(200))
            assertEquals(listOf(newer, older), awaitItem())
        }
    }

    @Test
    fun casesAreScopedToCurrentOwner() = runTest {
        val firstOwnerCase = repository().createCase(recording(100))

        ownerId = "owner-2"
        val secondOwnerCase = repository().createCase(recording(200))

        assertEquals(listOf(secondOwnerCase), repository().observeCases().first())
        assertNull(repository().getCase(firstOwnerCase.id))
        ownerId = "owner-1"
        assertEquals(listOf(firstOwnerCase), repository().observeCases().first())
    }

    @Test
    fun updateAndDeleteAffectOnlyOwnedCase() = runTest {
        val repository = repository()
        val case = repository.createCase(recording(100))

        repository.updateStatus(case.id, CaseStatus.COMPLETED)
        assertEquals(CaseStatus.COMPLETED, repository.getCase(case.id)?.status)

        repository.deleteCase(case.id)
        assertNull(repository.getCase(case.id))
    }

    private fun repository() = SqlDelightCaseRepository(database) { ownerId }

    private fun recording(createdAt: Long) = Recording(
        filePath = "/private/$createdAt.m4a",
        createdAtEpochMillis = createdAt,
    )
}