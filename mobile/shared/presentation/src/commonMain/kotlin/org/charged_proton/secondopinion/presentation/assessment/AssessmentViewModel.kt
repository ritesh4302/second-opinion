package org.charged_proton.secondopinion.presentation.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase

/**
 * Drives the assessment screen for one case: submits the case, streams
 * pipeline progress, shows the result, and records the pharmacist's decision.
 */
class AssessmentViewModel(
    private val caseId: String,
    private val requestAssessment: RequestAssessmentUseCase,
    private val getFeedback: GetFeedbackUseCase,
    private val submitFeedback: SubmitFeedbackUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssessmentUiState())
    val uiState: StateFlow<AssessmentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            requestAssessment(caseId)
                .catch { e ->
                    _uiState.update { it.copy(stage = null, errorMessage = e.message ?: "Assessment failed") }
                }
                .collect { progress ->
                    when (progress) {
                        is AssessmentProgress.InProgress ->
                            _uiState.update { it.copy(stage = progress.stage, errorMessage = null) }

                        is AssessmentProgress.Completed -> {
                            val existing = getFeedback(progress.assessment.id)
                            _uiState.update {
                                it.copy(
                                    stage = null,
                                    assessment = progress.assessment,
                                    decision = existing?.decision,
                                )
                            }
                        }

                        is AssessmentProgress.Failed ->
                            _uiState.update { it.copy(stage = null, errorMessage = progress.reason) }
                    }
                }
        }
    }

    fun onDecision(decision: PharmacistDecision, note: String? = null) {
        val assessment = _uiState.value.assessment ?: return
        if (_uiState.value.isSubmittingDecision) return
        _uiState.update { it.copy(isSubmittingDecision = true) }
        viewModelScope.launch {
            submitFeedback(Feedback(assessment.id, decision, note))
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingDecision = false, decision = decision) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSubmittingDecision = false,
                            errorMessage = e.message ?: "Could not save decision",
                        )
                    }
                }
        }
    }
}
