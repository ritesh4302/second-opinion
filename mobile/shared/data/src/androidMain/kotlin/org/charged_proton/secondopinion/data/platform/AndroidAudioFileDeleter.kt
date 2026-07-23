package org.charged_proton.secondopinion.data.platform

import java.io.File

/** Deletes the recorded .m4a from the app cache directory. */
class AndroidAudioFileDeleter : AudioFileDeleter {
    override fun delete(filePath: String) {
        File(filePath).delete()
    }
}
