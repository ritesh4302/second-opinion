package org.charged_proton.secondopinion.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.model.CaseStatus
import org.charged_proton.secondopinion.domain.usecase.ObserveCasesUseCase
import org.charged_proton.secondopinion.presentation.history.HistoryViewModel
import org.charged_proton.secondopinion.testutil.FakeCaseRepository
import org.charged_proton.secondopinion.testutil.testCase
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the history screen (ANDROID_APP.md §9): empty state,
 * case list rendering, and tap-to-open-assessment navigation. Uses a fake
 * case store so no Koin graph is involved.
 */
class HistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val caseRepository = FakeCaseRepository()

    private var openedCaseId: String? = null

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun setContent() {
        val viewModel = HistoryViewModel(ObserveCasesUseCase(caseRepository))
        composeRule.setContent {
            SecondOpinionTheme {
                HistoryScreen(onOpenCase = { openedCaseId = it }, viewModel = viewModel)
            }
        }
    }

    @Test
    fun noCases_showsEmptyMessage() {
        setContent()

        composeRule.onNodeWithText(string(R.string.history_empty)).assertIsDisplayed()
    }

    @Test
    fun cases_renderWithStatusLabels() {
        caseRepository.cases.value = listOf(
            testCase(id = "case-1", status = CaseStatus.COMPLETED, createdAtEpochMillis = 2_000L),
            testCase(id = "case-2", status = CaseStatus.RECORDED, createdAtEpochMillis = 1_000L),
        )
        setContent()

        composeRule.onNodeWithText(string(R.string.case_status_completed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.case_status_recorded)).assertIsDisplayed()
    }

    @Test
    fun tapCase_opensItsAssessment() {
        caseRepository.cases.value = listOf(
            testCase(id = "case-1", status = CaseStatus.COMPLETED),
        )
        setContent()

        composeRule.onNodeWithText(string(R.string.case_status_completed)).performClick()

        assertEquals("case-1", openedCaseId)
    }
}
