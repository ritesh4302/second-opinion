package org.charged_proton.secondopinion.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/** Encodes mono 16-bit PCM into an AAC-LC/.m4a file via MediaCodec + MediaMuxer. */
object AacM4aEncoder {

    private const val BIT_RATE = 48_000
    private const val TIMEOUT_US = 10_000L

    fun encode(samples: ShortArray, sampleRate: Int, outputFile: File) {
        val format = MediaFormat
            .createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
            .apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sampleOffset = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = requireNotNull(codec.getInputBuffer(inIndex))
                        inBuffer.clear()
                        val count = minOf(inBuffer.capacity() / 2, samples.size - sampleOffset)
                        val ptsUs = sampleOffset * 1_000_000L / sampleRate
                        if (count <= 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            inBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .put(samples, sampleOffset, count)
                            codec.queueInputBuffer(inIndex, 0, count * 2, ptsUs, 0)
                            sampleOffset += count
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outIndex >= 0) {
                        val outBuffer = requireNotNull(codec.getOutputBuffer(outIndex))
                        val isConfig =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && bufferInfo.size > 0) {
                            muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
