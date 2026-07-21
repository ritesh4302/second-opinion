package org.charged_proton.secondopinion.data.platform

/**
 * Reads a recorded audio file into memory for upload. Platform-specific
 * (java.io on Android) so the Ktor repository can stay in commonMain.
 */
fun interface AudioFileReader {
    fun read(filePath: String): ByteArray
}
