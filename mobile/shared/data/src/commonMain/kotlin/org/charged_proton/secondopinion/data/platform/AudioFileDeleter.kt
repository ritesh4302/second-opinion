package org.charged_proton.secondopinion.data.platform

/**
 * Deletes a recorded audio file from local storage (DPDP erasure).
 * Platform-specific (java.io on Android) so the repository stays in commonMain.
 */
fun interface AudioFileDeleter {
    fun delete(filePath: String)
}
