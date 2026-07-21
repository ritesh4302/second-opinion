package org.charged_proton.secondopinion.data.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.charged_proton.secondopinion.data.audio.AacM4aEncoder
import org.charged_proton.secondopinion.data.audio.SileroVadTrimmer
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.platform.AudioRecorder

/**
 * Android implementation of the [AudioRecorder] port backed by [AudioRecord].
 * Captures raw mono 16 kHz PCM, trims leading/trailing silence with Silero VAD
 * on stop(), then encodes the result to an AAC/.m4a file in the app cache.
 */
class VadTrimmingAudioRecorder(
    private val context: Context,
    private val trimmer: SileroVadTrimmer,
) : AudioRecorder {

    private class Session(val record: AudioRecord) {
        val chunks = ArrayList<ShortArray>()

        @Volatile
        var capturing = true
        lateinit var thread: Thread
    }

    private var session: Session? = null

    override val isRecording: Boolean
        get() = session != null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the UI before starting
    override fun start() {
        check(session == null) { "Recording already in progress" }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuffer, SAMPLE_RATE), // >= 0.5 s of audio headroom
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            record.release()
            "AudioRecord failed to initialise"
        }
        val newSession = Session(record)
        record.startRecording()
        newSession.thread = Thread {
            val buffer = ShortArray(READ_CHUNK_SAMPLES)
            while (newSession.capturing) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) newSession.chunks += buffer.copyOf(read)
            }
        }.apply {
            name = "vad-audio-capture"
            start()
        }
        session = newSession
    }

    override suspend fun stop(): Recording? {
        val active = session ?: return null
        session = null
        return withContext(Dispatchers.Default) {
            val samples = drain(active)
            val trimmed = trim(samples)
            val file = File(context.cacheDir, "symptom_recording_${System.currentTimeMillis()}.m4a")
            AacM4aEncoder.encode(trimmed, SAMPLE_RATE, file)
            Recording(
                filePath = file.absolutePath,
                createdAtEpochMillis = System.currentTimeMillis(),
                durationMillis = trimmed.size * 1000L / SAMPLE_RATE,
            )
        }
    }

    override fun release() {
        session?.let { drain(it) }
        session = null
    }

    /** Stops the capture thread and concatenates the collected PCM chunks. */
    private fun drain(active: Session): ShortArray {
        active.capturing = false
        active.thread.join()
        runCatching { active.record.stop() }
        active.record.release()
        val total = active.chunks.sumOf { it.size }
        val samples = ShortArray(total)
        var offset = 0
        for (chunk in active.chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        return samples
    }

    /** Cuts leading/trailing silence; falls back to the full buffer when no speech is found. */
    private fun trim(samples: ShortArray): ShortArray {
        val floats = FloatArray(samples.size) { samples[it] / 32768f }
        val range = trimmer.speechRange(floats) ?: return samples
        return samples.copyOfRange(range.first, range.last + 1)
    }

    private companion object {
        const val SAMPLE_RATE = SileroVadTrimmer.SAMPLE_RATE
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val READ_CHUNK_SAMPLES = 2048
    }
}
