package org.charged_proton.secondopinion.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase
import org.charged_proton.secondopinion.domain.model.PipelineStage

class SqlDelightUploadQueueStoreTest {
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
    fun queueSurvivesStoreRecreationAndPublishesUpdates() = runTest {
        val store = store()
        store.enqueue("case-1")

        store().observe("case-1").test {
            assertEquals(UploadQueueState.ENQUEUED, awaitItem()?.state)
            store.update(
                "case-1",
                UploadQueueState.PROCESSING,
                PipelineStage.TRANSCRIBING,
                attemptCount = 2,
            )
            val updated = awaitItem()
            assertEquals(UploadQueueState.PROCESSING, updated?.state)
            assertEquals(PipelineStage.TRANSCRIBING, updated?.pipelineStage)
            assertEquals(2, updated?.attemptCount)
        }
    }

    @Test
    fun enqueueResetsTerminalFailureForManualRetry() = runTest {
        val store = store()
        store.enqueue("case-1")
        store.update("case-1", UploadQueueState.FAILED, error = "bad network", attemptCount = 5)

        val retried = store.enqueue("case-1")

        assertEquals(UploadQueueState.ENQUEUED, retried.state)
        assertNull(retried.lastError)
    }

    @Test
    fun queueIsOwnerScopedAndDeletable() = runTest {
        store().enqueue("case-1")

        ownerId = "owner-2"
        assertNull(store().get("case-1"))
        store().enqueue("case-1")
        store().delete("case-1")
        assertNull(store().get("case-1"))

        ownerId = "owner-1"
        assertEquals(UploadQueueState.ENQUEUED, store().get("case-1")?.state)
    }

    private fun store() = SqlDelightUploadQueueStore(database) { ownerId }
}