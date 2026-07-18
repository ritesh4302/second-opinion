package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.platform.AudioRecorder

/** Releases recorder resources without saving; used on screen/ViewModel teardown. */
class ReleaseRecorderUseCase(private val audioRecorder: AudioRecorder) {

    operator fun invoke() {
        audioRecorder.release()
    }
}
