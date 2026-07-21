package org.charged_proton.secondopinion.presentation.symptom

import org.charged_proton.secondopinion.domain.model.Recording

/** Status of the symptom-capture flow; the UI layer maps this to localized strings. */
enum class SymptomStatus {
    IDLE,
    RECORDING,
    SAVED,
    PERMISSION_REQUIRED,
    CONSENT_DECLINED,
    ERROR,
}

data class SymptomUiState(
    val isRecording: Boolean = false,
    val status: SymptomStatus = SymptomStatus.IDLE,
    /** True while the tap-to-confirm patient-consent step is showing. */
    val awaitingConsent: Boolean = false,
    val lastRecording: Recording? = null,
    /** Case created from the last saved recording; drives "Get assessment" navigation. */
    val lastCaseId: String? = null,
)
