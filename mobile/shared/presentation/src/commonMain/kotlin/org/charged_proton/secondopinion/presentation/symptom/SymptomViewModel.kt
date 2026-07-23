package org.charged_proton.secondopinion.presentation.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase

/**
 * Holds the state of the symptom-capture screen. Recording starts with a
 * tap-to-confirm patient-consent step ([onRecordRequested] →
 * [onConsentConfirmed]/[onConsentDeclined]). Permission checks stay in the
 * UI layer; the UI calls [onPermissionDenied] when the microphone permission
 * is refused. On stop, the recording becomes a [SymptomUiState.lastCaseId]
 * the UI can submit for assessment.
 */
class SymptomViewModel(
    private val startRecording: StartRecordingUseCase,
    private val stopRecording: StopRecordingUseCase,
    private val releaseRecorder: ReleaseRecorderUseCase,
    private val createCase: CreateCaseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SymptomUiState())
    val uiState: StateFlow<SymptomUiState> = _uiState.asStateFlow()

    private var isStopping = false

    /** DPDP: whether the current capture flow's consent step was confirmed. */
    private var consentGranted = false

    /** Speak tapped: ask for patient consent before anything records. */
    fun onRecordRequested() {
        consentGranted = false
        _uiState.update { it.copy(awaitingConsent = true) }
    }

    /** Consent confirmed; the UI proceeds with the permission check + start. */
    fun onConsentConfirmed() {
        consentGranted = true
        _uiState.update { it.copy(awaitingConsent = false) }
    }

    fun onConsentDeclined() {
        consentGranted = false
        _uiState.update {
            it.copy(awaitingConsent = false, status = SymptomStatus.CONSENT_DECLINED)
        }
    }

    fun onStartRecording() {
        startRecording()
            .onSuccess {
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        status = SymptomStatus.RECORDING,
                        lastRecording = null,
                        lastCaseId = null,
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(isRecording = false, status = SymptomStatus.ERROR) }
            }
    }

    fun onStopRecording() {
        if (isStopping) return
        isStopping = true
        viewModelScope.launch {
            // stop() suspends while the recorder post-processes (VAD trim + encode)
            stopRecording()
                .onSuccess { stopped ->
                    if (stopped == null) {
                        _uiState.update { it.copy(isRecording = false, status = SymptomStatus.IDLE) }
                        return@launch
                    }
                    val recording = stopped.copy(consentConfirmed = consentGranted)
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            status = SymptomStatus.SAVED,
                            lastRecording = recording,
                        )
                    }
                    createCase(recording)
                        .onSuccess { case -> _uiState.update { it.copy(lastCaseId = case.id) } }
                        .onFailure { _uiState.update { it.copy(status = SymptomStatus.ERROR) } }
                }
                .onFailure {
                    _uiState.update { it.copy(isRecording = false, status = SymptomStatus.ERROR) }
                }
        }.invokeOnCompletion { isStopping = false }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(status = SymptomStatus.PERMISSION_REQUIRED) }
    }

    override fun onCleared() {
        releaseRecorder()
    }
}
