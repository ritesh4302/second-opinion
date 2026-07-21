package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.platform.AudioPlayer

/** Plays back a saved recording so the pharmacist can verify the capture. */
class PlayRecordingUseCase(private val audioPlayer: AudioPlayer) {

    operator fun invoke(recording: Recording, onCompleted: () -> Unit): Result<Unit> =
        runCatching { audioPlayer.play(recording.filePath, onCompleted) }
}
