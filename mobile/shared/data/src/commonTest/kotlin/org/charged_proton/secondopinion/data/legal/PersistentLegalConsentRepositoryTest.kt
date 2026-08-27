package org.charged_proton.secondopinion.data.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PersistentLegalConsentRepositoryTest {
    private val store = FakeLegalAcceptanceStore()
    private val repository = PersistentLegalConsentRepository(store) { 1_234L }

    @Test
    fun acceptancePersistsVersionAndTimestampForOneUser() = runTest {
        val accepted = repository.accept("user-1", "v2")

        assertEquals("v2", accepted.version)
        assertEquals(1_234L, accepted.acceptedAtEpochMillis)
        assertEquals(accepted, repository.getAcceptance("user-1"))
        assertNull(repository.getAcceptance("user-2"))
    }

    @Test
    fun blankUserCannotAccept() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.accept("", "v2") }
    }

    private class FakeLegalAcceptanceStore : LegalAcceptanceStore {
        private val versions = mutableMapOf<String, String>()
        private val times = mutableMapOf<String, Long>()
        override fun readVersion(userId: String) = versions[userId]
        override fun readAcceptedAt(userId: String) = times[userId]
        override suspend fun write(userId: String, version: String, acceptedAtEpochMillis: Long) {
            versions[userId] = version
            times[userId] = acceptedAtEpochMillis
        }
    }
}