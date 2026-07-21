package org.charged_proton.secondopinion.domain.platform

/**
 * Port for platform audio playback of saved recordings. Implemented per
 * platform in :shared:data (Android: MediaPlayer). One playback at a time:
 * starting a new one stops the previous one.
 */
interface AudioPlayer {

    /**
     * Plays the audio file at [filePath]; [onCompleted] fires when playback
     * finishes on its own (not when stopped). Throws if playback cannot start.
     */
    fun play(filePath: String, onCompleted: () -> Unit)

    /** Stops current playback and releases resources; safe to call when idle. */
    fun stop()
}
