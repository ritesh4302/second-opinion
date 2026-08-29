package org.charged_proton.secondopinion.ui.legal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalConsentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun acceptanceRequiresExplicitAcknowledgement() {
        var acceptCalls = 0
        composeRule.setContent {
            SecondOpinionTheme {
                LegalConsentScreen(false, false, onAccept = { acceptCalls++ })
            }
        }

        // performScrollTo: the consent content overflows small screens and
        // Compose test clicks do not auto-scroll to offscreen nodes.
        composeRule.onNodeWithText(string(R.string.legal_accept_continue))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.legal_acknowledgement))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.legal_accept_continue))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, acceptCalls)
    }

    @Test
    fun termsAndPrivacyAreReadableBeforeAcceptance() {
        composeRule.setContent {
            SecondOpinionTheme { LegalConsentScreen(false, false, onAccept = {}) }
        }

        composeRule.onNodeWithText(string(R.string.legal_terms_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.legal_terms_body)).assertIsDisplayed()
    }

    private fun string(id: Int): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(id)
}