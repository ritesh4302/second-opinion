package org.charged_proton.secondopinion.presentation.symptom

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.model.Recording
import org.charged_proton.secondopinion.domain.platform.AudioRecorder
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase
import org.charged_proton.secondopinion.presentation.testutil.FakeAudioRecorder
import org.charged_proton.secondopinion.presentation.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.presentation.testutil.testRecording
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SymptomViewModelTest {

    private val recorder = FakeAudioRecorder()
    private val caseRepository = FakeCaseRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SymptomViewModel(
        StartRecordingUseCase(recorder),
        StopRecordingUseCase(recorder),
        ReleaseRecorderUseCase(recorder),
        CreateCaseUseCase(caseRepository),
    )

    @Test
    fun initialState_isIdle() {
        assertEquals(SymptomUiState(), viewModel().uiState.value)
    }

    @Test
    fun startRecording_success_movesToRecording() {
        val vm = viewModel()

        vm.onStartRecording()

        assertEquals(SymptomStatus.RECORDING, vm.uiState.value.status)
        assertTrue(vm.uiState.value.isRecording)
    }

    @Test
    fun startRecording_failure_movesToError() {
        recorder.startError = IllegalStateException("mic busy")
        val vm = viewModel()

        vm.onStartRecording()

        assertEquals(SymptomStatus.ERROR, vm.uiState.value.status)
        assertFalse(vm.uiState.value.isRecording)
    }

    @Test
    fun stopRecording_savesRecordingAndCreatesCase() = runTest {
        recorder.stopResult = testRecording()
        val vm = viewModel()
        vm.onStartRecording()

        vm.onStopRecording()

        val state = vm.uiState.value
        assertEquals(SymptomStatus.SAVED, state.status)
        assertFalse(state.isRecording)
        assertEquals(testRecording(), state.lastRecording)
        assertEquals("case-1", state.lastCaseId)
        assertEquals(1, caseRepository.cases.value.size)
    }

    @Test
    fun stopRecording_nothingInProgress_returnsToIdle() = runTest {
        recorder.stopResult = null
        val vm = viewModel()

        vm.onStopRecording()

        assertEquals(SymptomStatus.IDLE, vm.uiState.value.status)
        assertNull(vm.uiState.value.lastCaseId)
    }

    @Test
    fun stopRecording_recorderThrows_movesToError() = runTest {
        recorder.stopError = RuntimeException("stop failed")
        val vm = viewModel()

        vm.onStopRecording()

        assertEquals(SymptomStatus.ERROR, vm.uiState.value.status)
    }

    @Test
    fun stopRecording_whileStopInFlight_secondCallIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        var stopCalls = 0
        val slowRecorder = object : AudioRecorder {
            override var isRecording = false
            override fun start() {
                isRecording = true
            }

            override suspend fun stop(): Recording? {
                stopCalls++
                gate.await() // simulates VAD trim + encode in progress
                isRecording = false
                return testRecording()
            }

            override fun release() = Unit
        }
        val vm = SymptomViewModel(
            StartRecordingUseCase(slowRecorder),
            StopRecordingUseCase(slowRecorder),
            ReleaseRecorderUseCase(slowRecorder),
            CreateCaseUseCase(caseRepository),
        )
        vm.onStartRecording()

        vm.onStopRecording()
        vm.onStopRecording() // re-entry while the first stop is still processing
        gate.complete(Unit)

        assertEquals(1, stopCalls)
        assertEquals(SymptomStatus.SAVED, vm.uiState.value.status)
        assertEquals(1, caseRepository.cases.value.size)
    }

    @Test
    fun stopRecording_caseCreationFails_movesToError() = runTest {
        recorder.stopResult = testRecording()
        caseRepository.createError = IllegalStateException("disk full")
        val vm = viewModel()

        vm.onStopRecording()

        assertEquals(SymptomStatus.ERROR, vm.uiState.value.status)
        assertNull(vm.uiState.value.lastCaseId)
    }

    @Test
    fun startRecording_afterSave_clearsPreviousRecordingAndCase() = runTest {
        recorder.stopResult = testRecording()
        val vm = viewModel()
        vm.onStopRecording()

        vm.onStartRecording()

        val state = vm.uiState.value
        assertNull(state.lastRecording)
        assertNull(state.lastCaseId)
        assertEquals(SymptomStatus.RECORDING, state.status)
    }

    @Test
    fun permissionDenied_setsPermissionRequiredStatus() {
        val vm = viewModel()

        vm.onPermissionDenied()

        assertEquals(SymptomStatus.PERMISSION_REQUIRED, vm.uiState.value.status)
    }

    @Test
    fun recordRequested_showsConsentStepWithoutRecording() {
        val vm = viewModel()

        vm.onRecordRequested()

        assertTrue(vm.uiState.value.awaitingConsent)
        assertFalse(vm.uiState.value.isRecording)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun consentConfirmed_clearsConsentStep() {
        val vm = viewModel()
        vm.onRecordRequested()

        vm.onConsentConfirmed()

        assertFalse(vm.uiState.value.awaitingConsent)
    }

    @Test
    fun consentDeclined_setsConsentDeclinedStatusWithoutRecording() {
        val vm = viewModel()
        vm.onRecordRequested()

        vm.onConsentDeclined()

        val state = vm.uiState.value
        assertFalse(state.awaitingConsent)
        assertEquals(SymptomStatus.CONSENT_DECLINED, state.status)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun clearingViewModel_releasesRecorder() {
        val store = ViewModelStore()
        store.put("vm", viewModel())

        store.clear()

        assertEquals(1, recorder.releaseCalls)
    }
}
