package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.platform.AudioRecorder

/** Stops the in-progress recording and returns it (null if none was active). */
class StopRecordingUseCase(private val audioRecorder: AudioRecorder) {

    suspend operator fun invoke(): Result<Recording?> = runCatching { audioRecorder.stop() }
}
