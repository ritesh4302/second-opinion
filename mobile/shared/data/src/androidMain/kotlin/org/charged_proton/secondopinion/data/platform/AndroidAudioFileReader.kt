package org.charged_proton.secondopinion.data.platform

import java.io.File

/** Reads the recorded .m4a from the app cache directory. */
class AndroidAudioFileReader : AudioFileReader {
    override fun read(filePath: String): ByteArray = File(filePath).readBytes()
}
