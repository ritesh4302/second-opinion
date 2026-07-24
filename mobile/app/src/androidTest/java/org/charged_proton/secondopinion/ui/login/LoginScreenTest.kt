package org.charged_proton.secondopinion.ui.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.charged_proton.secondopinion.testutil.FakeAuthClient
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the Google sign-in flow: tapping the button signing
 * the session in, and failure error messaging. Uses a fake auth client so no
 * Google Sign-In SDK or Koin graph is involved.
 */
class LoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val authClient = FakeAuthClient()
    private val viewModel = LoginViewModel(SignInUseCase(authClient))

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    @Before
    fun setUp() {
        composeRule.setContent {
            SecondOpinionTheme {
                LoginScreen(viewModel = viewModel)
            }
        }
    }

    @Test
    fun googleButton_isDisplayed() {
        composeRule.onNodeWithText(string(R.string.login_google)).assertIsDisplayed()
    }

    @Test
    fun tapSignIn_signsIn() {
        composeRule.onNodeWithText(string(R.string.login_google)).performClick()

        composeRule.waitForIdle()
        assertEquals(1, authClient.signInCalls)
        assertTrue(authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun signInFailure_showsError() {
        authClient.signInError = RuntimeException("boom")

        composeRule.onNodeWithText(string(R.string.login_google)).performClick()

        composeRule.onNodeWithText(string(R.string.login_error_sign_in_failed))
            .assertIsDisplayed()
    }
}
