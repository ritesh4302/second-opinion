package org.charged_proton.secondopinion.data.recorder

import kotlinx.cinterop.ExperimentalForeignApi
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of the [AudioRecorder] port backed by [AVAudioRecorder],
 * which captures 16 kHz mono AAC straight to a .m4a file (no separate encode
 * step as on Android). VAD silence trimming is not applied yet — the port
 * tolerates untrimmed audio; sherpa-onnx-based trimming is a follow-up.
 */
@OptIn(ExperimentalForeignApi::class)
class AvAudioRecorder : AudioRecorder {

    private var recorder: AVAudioRecorder? = null

    override val isRecording: Boolean
        get() = recorder != null

    override fun start() {
        check(recorder == null) { "Recording already in progress" }
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)
        val file = "${recordingsDir()}/symptom_recording_${epochMillis()}.m4a"
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to SAMPLE_RATE_HZ,
            AVNumberOfChannelsKey to 1,
        )
        val newRecorder = AVAudioRecorder(NSURL.fileURLWithPath(file), settings, null)
        check(newRecorder.prepareToRecord() && newRecorder.record()) {
            "AVAudioRecorder failed to start"
        }
        recorder = newRecorder
    }

    override suspend fun stop(): Recording? {
        val active = recorder ?: return null
        recorder = null
        val durationMillis = (active.currentTime * 1000).toLong()
        active.stop()
        val filePath = active.url.path ?: return null
        return Recording(
            filePath = filePath,
            createdAtEpochMillis = epochMillis(),
            durationMillis = durationMillis,
        )
    }

    override fun release() {
        recorder?.let {
            it.stop()
            it.deleteRecording()
        }
        recorder = null
    }

    private fun recordingsDir(): String {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        val dir = "$documents/recordings"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return dir
    }

    private fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000.0
    }
}
