package org.charged_proton.secondopinion.presentation.login

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.EmailAuthError
import org.charged_proton.secondopinion.domain.auth.EmailAuthException
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import org.charged_proton.secondopinion.domain.usecase.ResetPasswordUseCase
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase
import org.charged_proton.secondopinion.domain.usecase.SignInWithEmailUseCase
import org.charged_proton.secondopinion.domain.usecase.SignUpWithEmailUseCase
import org.charged_proton.secondopinion.presentation.testutil.FakeAuthClient

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val authClient = FakeAuthClient()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LoginViewModel(
        SignInUseCase(authClient),
        SignInWithEmailUseCase(authClient),
        SignUpWithEmailUseCase(authClient),
        ResetPasswordUseCase(authClient),
    )

    @Test
    fun initialState_isIdle() {
        assertEquals(LoginUiState(), viewModel().uiState.value)
    }

    @Test
    fun signIn_success_signsIn() {
        val vm = viewModel()

        vm.onSignIn()

        val state = vm.uiState.value
        assertEquals(false, state.isSubmitting)
        assertNull(state.error)
        assertEquals(1, authClient.signInCalls)
        assertEquals(true, authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun signIn_failure_showsError() {
        authClient.signInError = RuntimeException("boom")
        val vm = viewModel()

        vm.onSignIn()

        assertEquals(LoginError.SIGN_IN_FAILED, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun signIn_cancelled_showsNoError() {
        authClient.signInError = SignInCancelledException()
        val vm = viewModel()

        vm.onSignIn()

        assertNull(vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun signIn_retryAfterFailure_clearsError() {
        authClient.signInError = RuntimeException("boom")
        val vm = viewModel()
        vm.onSignIn()

        authClient.signInError = null
        vm.onSignIn()

        assertNull(vm.uiState.value.error)
        assertEquals(true, authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun submitEmail_signsInWithEmail() {
        val vm = viewModel()
        vm.onEmailChange("jane@pharmacy.org")
        vm.onPasswordChange("secret")

        vm.onSubmitEmail()

        assertEquals(1, authClient.emailSignInCalls)
        assertEquals(0, authClient.emailSignUpCalls)
        assertNull(vm.uiState.value.error)
        assertEquals(true, authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun submitEmail_inSignUpMode_signsUp() {
        val vm = viewModel()
        vm.onToggleSignUp()
        vm.onEmailChange("jane@pharmacy.org")
        vm.onPasswordChange("secret")

        vm.onSubmitEmail()

        assertEquals(1, authClient.emailSignUpCalls)
        assertEquals(0, authClient.emailSignInCalls)
        assertEquals(true, authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun submitEmail_withBlankFields_doesNothing() {
        val vm = viewModel()

        vm.onSubmitEmail()

        assertEquals(0, authClient.emailSignInCalls)
        assertEquals(LoginUiState(), vm.uiState.value)
    }

    @Test
    fun submitEmail_invalidCredentials_showsError() {
        authClient.signInError = EmailAuthException(EmailAuthError.INVALID_CREDENTIALS)
        val vm = viewModel()
        vm.onEmailChange("jane@pharmacy.org")
        vm.onPasswordChange("wrong")

        vm.onSubmitEmail()

        assertEquals(LoginError.INVALID_CREDENTIALS, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun submitEmail_emailInUse_showsError() {
        authClient.signInError = EmailAuthException(EmailAuthError.EMAIL_ALREADY_IN_USE)
        val vm = viewModel()
        vm.onToggleSignUp()
        vm.onEmailChange("jane@pharmacy.org")
        vm.onPasswordChange("secret")

        vm.onSubmitEmail()

        assertEquals(LoginError.EMAIL_ALREADY_IN_USE, vm.uiState.value.error)
    }

    @Test
    fun editingFields_clearsError() {
        authClient.signInError = EmailAuthException(EmailAuthError.INVALID_CREDENTIALS)
        val vm = viewModel()
        vm.onEmailChange("jane@pharmacy.org")
        vm.onPasswordChange("wrong")
        vm.onSubmitEmail()

        vm.onPasswordChange("corrected")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun forgotPassword_sendsTrimmedEmailAndShowsGenericConfirmation() {
        val vm = viewModel()
        vm.onEmailChange(" jane@pharmacy.org ")

        vm.onForgotPassword()

        assertEquals(1, authClient.passwordResetCalls)
        assertEquals("jane@pharmacy.org", authClient.lastEmail)
        assertEquals(true, vm.uiState.value.passwordResetSent)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun forgotPassword_withInvalidEmail_showsValidationError() {
        val vm = viewModel()
        vm.onEmailChange("invalid")

        vm.onForgotPassword()

        assertEquals(LoginError.INVALID_EMAIL, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.passwordResetSent)
        assertEquals(0, authClient.passwordResetCalls)
    }

    @Test
    fun forgotPassword_withBlankEmail_doesNothing() {
        val vm = viewModel()

        vm.onForgotPassword()

        assertEquals(0, authClient.passwordResetCalls)
    }

    @Test
    fun forgotPassword_deliveryFailure_showsGenericError() {
        authClient.signInError = RuntimeException("network down")
        val vm = viewModel()
        vm.onEmailChange("jane@pharmacy.org")

        vm.onForgotPassword()

        assertEquals(LoginError.PASSWORD_RESET_FAILED, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.passwordResetSent)
    }
}
