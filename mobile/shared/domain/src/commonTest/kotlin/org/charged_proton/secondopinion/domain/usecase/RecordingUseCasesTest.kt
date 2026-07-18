package org.charged_proton.secondopinion.domain.usecase

import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.testutil.FakeAudioRecorder
import org.charged_proton.secondopinion.domain.testutil.testRecording
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordingUseCasesTest {

    private val recorder = FakeAudioRecorder()

    @Test
    fun startRecording_success_returnsSuccessAndStartsRecorder() {
        val result = StartRecordingUseCase(recorder)()

        assertTrue(result.isSuccess)
        assertEquals(1, recorder.startCalls)
        assertTrue(recorder.isRecording)
    }

    @Test
    fun startRecording_recorderThrows_wrapsInFailure() {
        val boom = IllegalStateException("mic busy")
        recorder.startError = boom

        val result = StartRecordingUseCase(recorder)()

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun stopRecording_returnsRecording() = runTest {
        val recording = testRecording()
        recorder.stopResult = recording

        val result = StopRecordingUseCase(recorder)()

        assertTrue(result.isSuccess)
        assertEquals(recording, result.getOrNull())
    }

    @Test
    fun stopRecording_nothingInProgress_returnsSuccessNull() = runTest {
        recorder.stopResult = null

        val result = StopRecordingUseCase(recorder)()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun stopRecording_recorderThrows_wrapsInFailure() = runTest {
        val boom = RuntimeException("stop failed")
        recorder.stopError = boom

        val result = StopRecordingUseCase(recorder)()

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun releaseRecorder_delegatesToRecorder() {
        ReleaseRecorderUseCase(recorder)()

        assertEquals(1, recorder.releaseCalls)
    }
}
