package org.charged_proton.secondopinion.data.audio

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the speech portion of a mono 16 kHz PCM buffer using the Silero VAD
 * model (via sherpa-onnx) so leading/trailing silence can be trimmed before
 * upload. Pauses between speech segments are kept — only the edges are cut.
 *
 * The model file (silero_vad.onnx) is loaded from the app's assets.
 */
class SileroVadTrimmer(
    private val assets: AssetManager,
    private val modelAssetPath: String = "silero_vad.onnx",
) {

    private val vad: Vad by lazy {
        Vad(
            assetManager = assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelAssetPath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW_SIZE,
                    maxSpeechDuration = 20f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
    }

    /**
     * Returns the sample range (inclusive) that contains speech, padded by
     * [PADDING_SECONDS] on both sides, or null when no speech is detected.
     * Samples must be mono 16 kHz floats in [-1, 1].
     */
    fun speechRange(samples: FloatArray): IntRange? {
        var firstStart = -1
        var lastEnd = -1

        fun drainSegments() {
            while (!vad.empty()) {
                val segment = vad.front()
                if (firstStart < 0) firstStart = segment.start
                lastEnd = max(lastEnd, segment.start + segment.samples.size)
                vad.pop()
            }
        }

        vad.reset()
        var offset = 0
        while (offset + WINDOW_SIZE <= samples.size) {
            vad.acceptWaveform(samples.copyOfRange(offset, offset + WINDOW_SIZE))
            drainSegments()
            offset += WINDOW_SIZE
        }
        vad.flush()
        drainSegments()

        if (firstStart < 0) return null
        val padding = (PADDING_SECONDS * SAMPLE_RATE).toInt()
        val from = max(firstStart - padding, 0)
        val toInclusive = min(lastEnd + padding, samples.size) - 1
        return from..toInclusive
    }

    fun release() {
        vad.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val WINDOW_SIZE = 512
        private const val PADDING_SECONDS = 0.2f
    }
}
