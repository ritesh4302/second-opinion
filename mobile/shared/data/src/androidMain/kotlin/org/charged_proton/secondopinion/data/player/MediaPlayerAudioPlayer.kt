package org.charged_proton.secondopinion.data.player

import android.media.MediaPlayer
import org.charged_proton.secondopinion.domain.platform.AudioPlayer

/**
 * [AudioPlayer] backed by [MediaPlayer]. Plays local .m4a recordings from the
 * app cache; single playback at a time (a new play stops the previous one).
 */
class MediaPlayerAudioPlayer : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun play(filePath: String, onCompleted: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(filePath)
            setOnCompletionListener {
                this@MediaPlayerAudioPlayer.stop()
                onCompleted()
            }
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.let { current ->
            runCatching { current.stop() }
            current.release()
        }
        player = null
    }
}
