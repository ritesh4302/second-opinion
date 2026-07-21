package org.charged_proton.secondopinion.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopPlaybackUseCase

data class HistoryUiState(
    val cases: List<SymptomCase> = emptyList(),
    /** Case whose recording is currently playing back, if any. */
    val playingCaseId: String? = null,
)

/**
 * Streams the case list (newest first) for the history screen and drives
 * recording playback so the pharmacist can verify a capture.
 */
class HistoryViewModel(
    observeCases: ObserveCasesUseCase,
    private val playRecording: PlayRecordingUseCase,
    private val stopPlayback: StopPlaybackUseCase,
) : ViewModel() {

    private val playingCaseId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> =
        combine(observeCases(), playingCaseId) { cases, playing ->
            HistoryUiState(cases = cases, playingCaseId = playing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HistoryUiState(),
        )

    /** Plays the case's recording, or stops it when it is already playing. */
    fun onTogglePlayback(case: SymptomCase) {
        stopPlayback()
        if (playingCaseId.value == case.id) {
            playingCaseId.value = null
            return
        }
        playRecording(case.recording) { playingCaseId.value = null }
            .onSuccess { playingCaseId.value = case.id }
            .onFailure { playingCaseId.value = null }
    }

    override fun onCleared() {
        stopPlayback()
    }
}
