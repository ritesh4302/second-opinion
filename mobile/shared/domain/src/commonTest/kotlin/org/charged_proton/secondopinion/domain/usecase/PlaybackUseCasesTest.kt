package org.charged_proton.secondopinion.domain.usecase

import org.charged_proton.secondopinion.domain.testutil.FakeAudioPlayer
import org.charged_proton.secondopinion.domain.testutil.testRecording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaybackUseCasesTest {

    private val player = FakeAudioPlayer()

    @Test
    fun playRecording_success_playsTheRecordingFile() {
        val recording = testRecording(filePath = "/tmp/case.m4a")

        val result = PlayRecordingUseCase(player)(recording) {}

        assertTrue(result.isSuccess)
        assertEquals(listOf("/tmp/case.m4a"), player.playedFilePaths)
    }

    @Test
    fun playRecording_completionCallbackIsWired() {
        var completed = false

        PlayRecordingUseCase(player)(testRecording()) { completed = true }
        player.completePlayback()

        assertTrue(completed)
    }

    @Test
    fun playRecording_playerThrows_wrapsInFailure() {
        val boom = IllegalStateException("bad file")
        player.playError = boom

        val result = PlayRecordingUseCase(player)(testRecording()) {}

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun stopPlayback_delegatesToPlayer() {
        val result = StopPlaybackUseCase(player)()

        assertTrue(result.isSuccess)
        assertEquals(1, player.stopCalls)
    }
}
