package org.charged_proton.secondopinion.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/** Deletes the recorded .m4a from the app container (DPDP erasure). */
@OptIn(ExperimentalForeignApi::class)
class IosAudioFileDeleter : AudioFileDeleter {
    override fun delete(filePath: String) {
        NSFileManager.defaultManager.removeItemAtPath(filePath, error = null)
    }
}
