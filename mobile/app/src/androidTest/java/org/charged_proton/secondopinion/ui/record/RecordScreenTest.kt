package org.charged_proton.secondopinion.ui.record

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.usecase.CreateCaseUseCase
import org.charged_proton.secondopinion.domain.usecase.ReleaseRecorderUseCase
import org.charged_proton.secondopinion.domain.usecase.StartRecordingUseCase
import org.charged_proton.secondopinion.domain.usecase.StopRecordingUseCase
import org.charged_proton.secondopinion.presentation.symptom.SymptomViewModel
import org.charged_proton.secondopinion.testutil.FakeAudioRecorder
import org.charged_proton.secondopinion.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the critical record-screen flows (ANDROID_APP.md §9):
 * consent step, Speak/Stop toggle, and permission-denied messaging. Uses a
 * fake recorder and case store so no microphone or Koin graph is involved.
 */
class RecordScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val recorder = FakeAudioRecorder()
    private val caseRepository = FakeCaseRepository()
    private val viewModel = SymptomViewModel(
        StartRecordingUseCase(recorder),
        StopRecordingUseCase(recorder),
        ReleaseRecorderUseCase(recorder),
        CreateCaseUseCase(caseRepository),
    )

    private var openedCaseId: String? = null
    private var historyOpened = false

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        composeRule.setContent {
            SecondOpinionTheme {
                RecordScreen(
                    onOpenAssessment = { openedCaseId = it },
                    onOpenHistory = { historyOpened = true },
                    viewModel = viewModel,
                )
            }
        }
    }

    /** Taps Speak and confirms the consent dialog so recording starts. */
    private fun startRecordingWithConsent() {
        composeRule.onNodeWithText(string(R.string.speak)).performClick()
        composeRule.onNodeWithText(string(R.string.consent_confirm)).performClick()
    }

    @Test
    fun initialState_showsSpeakButtonAndIdlePrompt() {
        composeRule.onNodeWithText(string(R.string.speak)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.prompt_describe_symptoms)).assertIsDisplayed()
    }

    @Test
    fun tapSpeak_showsConsentDialogWithoutRecording() {
        composeRule.onNodeWithText(string(R.string.speak)).performClick()

        composeRule.onNodeWithText(string(R.string.consent_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.consent_body)).assertIsDisplayed()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun decliningConsent_cancelsAndExplains() {
        composeRule.onNodeWithText(string(R.string.speak)).performClick()

        composeRule.onNodeWithText(string(R.string.consent_decline)).performClick()

        composeRule.onNodeWithText(string(R.string.status_consent_declined)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.speak)).assertIsDisplayed()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun confirmingConsent_togglesToStopWithRecordingStatus() {
        startRecordingWithConsent()

        composeRule.onNodeWithText(string(R.string.stop)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.status_recording)).assertIsDisplayed()
        assertTrue(recorder.isRecording)
    }

    @Test
    fun tapStop_savesRecordingAndOffersAssessment() {
        startRecordingWithConsent()

        composeRule.onNodeWithText(string(R.string.stop)).performClick()

        composeRule.onNodeWithText(string(R.string.speak)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.status_saved)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.get_assessment)).assertIsDisplayed()
        assertEquals(1, caseRepository.cases.value.size)
    }

    @Test
    fun tapGetAssessment_navigatesWithCreatedCaseId() {
        startRecordingWithConsent()
        composeRule.onNodeWithText(string(R.string.stop)).performClick()

        composeRule.onNodeWithText(string(R.string.get_assessment)).performClick()

        assertEquals("case-1", openedCaseId)
    }

    @Test
    fun permissionDenied_showsPermissionRequiredMessage() {
        composeRule.runOnUiThread { viewModel.onPermissionDenied() }

        composeRule.onNodeWithText(string(R.string.status_permission_required))
            .assertIsDisplayed()
    }

    @Test
    fun tapViewHistory_invokesCallback() {
        composeRule.onNodeWithText(string(R.string.view_history)).performClick()

        assertTrue(historyOpened)
    }
}
