package org.charged_proton.secondopinion.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.charged_proton.secondopinion.domain.model.SymptomCase
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase

data class HistoryUiState(
    val cases: List<SymptomCase> = emptyList(),
)

/** Streams the case list (newest first) for the history screen. */
class HistoryViewModel(
    observeCases: ObserveCasesUseCase,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = observeCases()
        .map(::HistoryUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HistoryUiState(),
        )
}
