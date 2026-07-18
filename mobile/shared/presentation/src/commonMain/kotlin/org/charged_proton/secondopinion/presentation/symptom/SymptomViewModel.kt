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
 * Holds the state of the symptom-capture screen. Permission checks stay in the
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
                .onSuccess { recording ->
                    if (recording == null) {
                        _uiState.update { it.copy(isRecording = false, status = SymptomStatus.IDLE) }
                        return@launch
                    }
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
