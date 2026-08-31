package org.charged_proton.secondopinion.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** Reads the recorded .m4a from the app container. */
class IosAudioFileReader : AudioFileReader {
    override fun read(filePath: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(filePath)
            ?: error("Cannot read audio file: $filePath")
        return data.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    if (isNotEmpty()) usePinned { memcpy(it.addressOf(0), bytes, length) }
}
