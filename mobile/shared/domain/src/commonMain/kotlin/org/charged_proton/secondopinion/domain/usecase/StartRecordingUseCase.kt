package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.platform.AudioRecorder

/** Starts capturing a new symptom recording. */
class StartRecordingUseCase(private val audioRecorder: AudioRecorder) {

    operator fun invoke(): Result<Unit> = runCatching { audioRecorder.start() }
}
