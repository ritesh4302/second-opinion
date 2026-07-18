package org.charged_proton.secondopinion.domain.platform

import org.charged_proton.secondopinion.domain.model.Recording

/**
 * Port for platform audio capture. Implemented per platform in :shared:data
 * (Android: MediaRecorder). The caller is responsible for ensuring the
 * microphone permission is granted before calling [start].
 */
interface AudioRecorder {

    val isRecording: Boolean

    /** Starts a new recording. Throws if the recorder cannot be started. */
    fun start()

    /**
     * Stops the current recording and returns it, or null if none was in
     * progress. Suspending: implementations may post-process the audio
     * (e.g. VAD silence trimming, encoding) before saving.
     */
    suspend fun stop(): Recording?

    /** Releases resources without saving; safe to call when not recording. */
    fun release()
}
