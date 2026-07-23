package org.charged_proton.secondopinion.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.usecase.DeleteCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.domain.usecase.PlayRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopPlaybackUseCase

data class HistoryUiState(
    val cases: List<SymptomCase> = emptyList(),
    /** Case whose recording is currently playing back, if any. */
    val playingCaseId: String? = null,
    /** Case awaiting delete confirmation, if any. */
    val confirmingDeleteCaseId: String? = null,
)

/**
 * Streams the case list (newest first) for the history screen, drives
 * recording playback so the pharmacist can verify a capture, and handles
 * confirm-then-delete case erasure (DPDP).
 */
class HistoryViewModel(
    observeCases: ObserveCasesUseCase,
    private val playRecording: PlayRecordingUseCase,
    private val stopPlayback: StopPlaybackUseCase,
    private val deleteCase: DeleteCaseUseCase,
) : ViewModel() {

    private val playingCaseId = MutableStateFlow<String?>(null)
    private val confirmingDeleteCaseId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> =
        combine(observeCases(), playingCaseId, confirmingDeleteCaseId) { cases, playing, deleting ->
            HistoryUiState(
                cases = cases,
                playingCaseId = playing,
                confirmingDeleteCaseId = deleting,
            )
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

    /** Delete tapped on a case: ask for confirmation first. */
    fun onDeleteRequested(case: SymptomCase) {
        confirmingDeleteCaseId.value = case.id
    }

    fun onDeleteDismissed() {
        confirmingDeleteCaseId.value = null
    }

    fun onDeleteConfirmed() {
        val caseId = confirmingDeleteCaseId.value ?: return
        confirmingDeleteCaseId.value = null
        if (playingCaseId.value == caseId) {
            stopPlayback()
            playingCaseId.value = null
        }
        viewModelScope.launch { deleteCase(caseId) }
    }

    override fun onCleared() {
        stopPlayback()
    }
}
