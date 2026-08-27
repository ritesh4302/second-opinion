package org.charged_proton.secondopinion.data.legal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedPreferencesLegalAcceptanceStore(context: Context) : LegalAcceptanceStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readVersion(userId: String): String? =
        prefs.getString(versionKey(userId), null)

    override fun readAcceptedAt(userId: String): Long? =
        if (prefs.contains(timeKey(userId))) prefs.getLong(timeKey(userId), 0L) else null

    override suspend fun write(userId: String, version: String, acceptedAtEpochMillis: Long) =
        withContext(Dispatchers.IO) {
            check(
                prefs.edit()
                    .putString(versionKey(userId), version)
                    .putLong(timeKey(userId), acceptedAtEpochMillis)
                    .commit(),
            ) { "Could not persist legal acceptance" }
        }

    private fun versionKey(userId: String) = "legal_version_$userId"
    private fun timeKey(userId: String) = "legal_accepted_at_$userId"

    private companion object {
        const val PREFS_NAME = "second_opinion_legal"
    }
}