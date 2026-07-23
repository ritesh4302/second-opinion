package org.charged_proton.secondopinion.ui.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.usecase.RequestOtpUseCase
import org.charged_proton.secondopinion.domain.usecase.VerifyOtpUseCase
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.charged_proton.secondopinion.testutil.FakeAuthClient
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the phone sign-in flow: phone → OTP step transition,
 * OTP verification signing the session in, and wrong-code error messaging.
 * Uses a fake auth client so no Firebase or Koin graph is involved.
 */
class LoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val authClient = FakeAuthClient()
    private val viewModel = LoginViewModel(
        RequestOtpUseCase(authClient),
        VerifyOtpUseCase(authClient),
    )

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    @Before
    fun setUp() {
        composeRule.setContent {
            SecondOpinionTheme {
                LoginScreen(viewModel = viewModel)
            }
        }
    }

    /** Enters a phone number and requests the OTP, landing on the OTP step. */
    private fun requestOtp(phoneNumber: String = "+919876543210") {
        composeRule.onNodeWithText(string(R.string.login_phone_label))
            .performTextInput(phoneNumber)
        composeRule.onNodeWithText(string(R.string.login_send_otp)).performClick()
    }

    @Test
    fun sendCode_movesToOtpStep() {
        requestOtp()

        composeRule.onNodeWithText(string(R.string.login_otp_label)).assertIsDisplayed()
        assertEquals(listOf("+919876543210"), authClient.requestedPhoneNumbers)
    }

    @Test
    fun verifyOtp_signsIn() {
        requestOtp()

        composeRule.onNodeWithText(string(R.string.login_otp_label)).performTextInput("123456")
        composeRule.onNodeWithText(string(R.string.login_verify)).performClick()

        composeRule.waitForIdle()
        assertTrue(authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun wrongOtp_showsError() {
        authClient.verifyOtpError = InvalidOtpException()
        requestOtp()

        composeRule.onNodeWithText(string(R.string.login_otp_label)).performTextInput("000000")
        composeRule.onNodeWithText(string(R.string.login_verify)).performClick()

        composeRule.onNodeWithText(string(R.string.login_error_invalid_otp)).assertIsDisplayed()
    }

    @Test
    fun changeNumber_returnsToPhoneStep() {
        requestOtp()

        composeRule.onNodeWithText(string(R.string.login_change_number)).performClick()

        composeRule.onNodeWithText(string(R.string.login_send_otp)).assertIsDisplayed()
    }
}
