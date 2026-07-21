package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.platform.AudioPlayer

/** Stops any in-progress recording playback. */
class StopPlaybackUseCase(private val audioPlayer: AudioPlayer) {

    operator fun invoke(): Result<Unit> = runCatching { audioPlayer.stop() }
}
