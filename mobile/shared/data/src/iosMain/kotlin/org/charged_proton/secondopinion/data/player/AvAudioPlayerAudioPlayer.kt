package org.charged_proton.secondopinion.data.player

import kotlinx.cinterop.ExperimentalForeignApi
import org.charged_proton.secondopinion.domain.platform.AudioPlayer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * [AudioPlayer] backed by [AVAudioPlayer]. Plays local .m4a recordings from
 * the app container; single playback at a time (a new play stops the previous
 * one), mirroring the Android MediaPlayer implementation.
 */
@OptIn(ExperimentalForeignApi::class)
class AvAudioPlayerAudioPlayer : AudioPlayer {

    private var player: AVAudioPlayer? = null
    private var delegate: CompletionDelegate? = null

    override fun play(filePath: String, onCompleted: () -> Unit) {
        stop()
        val newPlayer = AVAudioPlayer(NSURL.fileURLWithPath(filePath), error = null)
        val newDelegate = CompletionDelegate {
            stop()
            onCompleted()
        }
        newPlayer.delegate = newDelegate
        check(newPlayer.prepareToPlay() && newPlayer.play()) { "AVAudioPlayer failed to start" }
        player = newPlayer
        delegate = newDelegate
    }

    override fun stop() {
        player?.let { current ->
            current.delegate = null
            current.stop()
        }
        player = null
        delegate = null
    }

    private class CompletionDelegate(
        private val onFinished: () -> Unit,
    ) : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onFinished()
        }
    }
}
