package org.charged_proton.secondopinion.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.usecase.AcceptLegalTermsUseCase
import org.charged_proton.secondopinion.domain.usecase.GetLegalAcceptanceUseCase

data class LegalConsentUiState(
    val isAccepted: Boolean = false,
    val isAccepting: Boolean = false,
    val acceptanceFailed: Boolean = false,
)

class LegalConsentViewModel(
    private val userId: String,
    getAcceptance: GetLegalAcceptanceUseCase,
    private val acceptTerms: AcceptLegalTermsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LegalConsentUiState(isAccepted = getAcceptance(userId)),
    )
    val uiState: StateFlow<LegalConsentUiState> = _uiState.asStateFlow()

    fun onAccept() {
        if (_uiState.value.isAccepting) return
        _uiState.update { it.copy(isAccepting = true, acceptanceFailed = false) }
        viewModelScope.launch {
            try {
                acceptTerms(userId)
                _uiState.value = LegalConsentUiState(isAccepted = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(isAccepting = false, acceptanceFailed = true)
                }
            }
        }
    }
}