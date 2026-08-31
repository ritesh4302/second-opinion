package org.charged_proton.secondopinion.data.legal

import platform.Foundation.NSUserDefaults

/** NSUserDefaults counterpart of the Android SharedPreferences store. */
class UserDefaultsLegalAcceptanceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : LegalAcceptanceStore {

    override fun readVersion(userId: String): String? =
        defaults.stringForKey(versionKey(userId))

    override fun readAcceptedAt(userId: String): Long? =
        if (defaults.objectForKey(timeKey(userId)) != null) {
            defaults.integerForKey(timeKey(userId))
        } else {
            null
        }

    override suspend fun write(userId: String, version: String, acceptedAtEpochMillis: Long) {
        defaults.setObject(version, versionKey(userId))
        defaults.setInteger(acceptedAtEpochMillis, timeKey(userId))
    }

    private fun versionKey(userId: String) = "legal_version_$userId"
    private fun timeKey(userId: String) = "legal_accepted_at_$userId"
}
