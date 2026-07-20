package org.charged_proton.secondopinion.ui.assessment

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.model.AssessmentProgress
import org.charged_proton.secondopinion.domain.model.Feedback
import org.charged_proton.secondopinion.domain.model.OtcAdvice
import org.charged_proton.secondopinion.domain.model.PharmacistDecision
import org.charged_proton.secondopinion.domain.model.PipelineStage
import org.charged_proton.secondopinion.domain.model.RedFlag
import org.charged_proton.secondopinion.domain.usecase.GetFeedbackUseCase
import org.charged_proton.secondopinion.domain.usecase.RequestAssessmentUseCase
import org.charged_proton.secondopinion.domain.usecase.SubmitFeedbackUseCase
import org.charged_proton.secondopinion.presentation.assessment.AssessmentViewModel
import org.charged_proton.secondopinion.testutil.FakeAssessmentRepository
import org.charged_proton.secondopinion.testutil.testAssessment
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the assessment screen (ANDROID_APP.md §9): pipeline
 * progress, referral banner, prescription-drug badge, and the pharmacist
 * accept/reject/override decision bar. Uses a fake repository so no backend
 * or Koin graph is involved.
 */
class AssessmentScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeAssessmentRepository()

    private fun string(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun setContent(progress: Flow<AssessmentProgress>) {
        repository.progressFlow = progress
        val viewModel = AssessmentViewModel(
            "case-1",
            RequestAssessmentUseCase(repository),
            GetFeedbackUseCase(repository),
            SubmitFeedbackUseCase(repository),
        )
        composeRule.setContent {
            SecondOpinionTheme {
                AssessmentScreen(caseId = "case-1", viewModel = viewModel)
            }
        }
    }

    @Test
    fun inProgress_showsAnalyzingStage() {
        setContent(flowOf(AssessmentProgress.InProgress(PipelineStage.UPLOADING)))

        composeRule
            .onNodeWithText(string(R.string.assessment_in_progress, PipelineStage.UPLOADING.name))
            .assertIsDisplayed()
    }

    @Test
    fun failed_showsReason() {
        setContent(flowOf(AssessmentProgress.Failed("Case not found")))

        composeRule.onNodeWithText("Case not found").assertIsDisplayed()
    }

    @Test
    fun completed_rendersSummaryConditionsAndGuidance() {
        val assessment = testAssessment(
            otcGuidance = listOf(OtcAdvice("Paracetamol 500 mg", "1 tablet", "For fever.")),
        )
        setContent(flowOf(AssessmentProgress.Completed(assessment)))

        composeRule.onNodeWithText(assessment.symptomSummary).assertIsDisplayed()
        composeRule.onNodeWithText("Viral fever — 70%").assertIsDisplayed()
        composeRule.onNodeWithText("Paracetamol 500 mg").assertIsDisplayed()
        composeRule.onNodeWithText(assessment.disclaimer).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.prescription_drug_label)).assertCountEquals(0)
    }

    @Test
    fun prescriptionMedicine_showsPrescriptionDrugBadge() {
        val assessment = testAssessment(
            otcGuidance = listOf(
                OtcAdvice("Paracetamol 500 mg", "1 tablet", "For fever."),
                OtcAdvice("Azithromycin 500 mg", "1 tablet daily", "Schedule H.", prescription = true),
            ),
        )
        setContent(flowOf(AssessmentProgress.Completed(assessment)))

        composeRule
            .onNodeWithText(string(R.string.prescription_drug_label))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun redFlags_showReferralBanner() {
        val assessment = testAssessment(
            redFlags = listOf(RedFlag("Chest pain radiating to arm", "Send to hospital now")),
        )
        setContent(flowOf(AssessmentProgress.Completed(assessment)))

        composeRule.onNodeWithText(string(R.string.refer_to_doctor)).assertIsDisplayed()
        composeRule.onNodeWithText("Chest pain radiating to arm").assertIsDisplayed()
        composeRule.onNodeWithText("Send to hospital now").assertIsDisplayed()
    }

    @Test
    fun decisionBar_offersAcceptRejectAndOverride() {
        setContent(flowOf(AssessmentProgress.Completed(testAssessment())))

        composeRule.onNodeWithText(string(R.string.decision_accept))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.decision_reject))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.decision_override))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tapAccept_recordsDecision() {
        val assessment = testAssessment()
        setContent(flowOf(AssessmentProgress.Completed(assessment)))

        composeRule.onNodeWithText(string(R.string.decision_accept))
            .performScrollTo().performClick()

        composeRule
            .onNodeWithText(string(R.string.decision_recorded, PharmacistDecision.ACCEPTED.name))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            Feedback(assessment.id, PharmacistDecision.ACCEPTED),
            repository.feedbackByAssessmentId[assessment.id],
        )
    }

    @Test
    fun previousDecision_isPreloadedInsteadOfButtons() {
        val assessment = testAssessment()
        repository.feedbackByAssessmentId[assessment.id] =
            Feedback(assessment.id, PharmacistDecision.OVERRIDDEN)
        setContent(flowOf(AssessmentProgress.Completed(assessment)))

        composeRule
            .onNodeWithText(string(R.string.decision_recorded, PharmacistDecision.OVERRIDDEN.name))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.decision_accept)).assertCountEquals(0)
    }
}
