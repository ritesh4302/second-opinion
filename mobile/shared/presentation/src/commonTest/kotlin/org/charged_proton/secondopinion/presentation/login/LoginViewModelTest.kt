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
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.auth.InvalidPhoneNumberException
import org.charged_proton.secondopinion.domain.usecase.RequestOtpUseCase
import org.charged_proton.secondopinion.domain.usecase.VerifyOtpUseCase
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
        RequestOtpUseCase(authClient),
        VerifyOtpUseCase(authClient),
    )

    @Test
    fun initialState_isPhoneStep() {
        assertEquals(LoginUiState(), viewModel().uiState.value)
    }

    @Test
    fun requestOtp_success_movesToOtpStep() {
        val vm = viewModel()
        vm.onPhoneNumberChanged(" +919876543210 ")

        vm.onRequestOtp()

        val state = vm.uiState.value
        assertEquals(LoginStep.OTP, state.step)
        assertEquals(false, state.isSubmitting)
        assertNull(state.error)
        assertEquals(listOf("+919876543210"), authClient.requestedPhoneNumbers)
    }

    @Test
    fun requestOtp_invalidPhone_showsError() {
        authClient.requestOtpError = InvalidPhoneNumberException("12345")
        val vm = viewModel()
        vm.onPhoneNumberChanged("12345")

        vm.onRequestOtp()

        val state = vm.uiState.value
        assertEquals(LoginStep.PHONE, state.step)
        assertEquals(LoginError.INVALID_PHONE, state.error)
    }

    @Test
    fun typing_clearsError() {
        authClient.requestOtpError = InvalidPhoneNumberException("12345")
        val vm = viewModel()
        vm.onPhoneNumberChanged("12345")
        vm.onRequestOtp()

        vm.onPhoneNumberChanged("123456")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun verifyOtp_success_signsIn() {
        val vm = viewModel()
        vm.onPhoneNumberChanged("+919876543210")
        vm.onRequestOtp()
        vm.onOtpChanged("123456")

        vm.onVerifyOtp()

        assertNull(vm.uiState.value.error)
        assertEquals(listOf("123456"), authClient.verifiedCodes)
        assertEquals(true, authClient.authState.value is AuthState.SignedIn)
    }

    @Test
    fun verifyOtp_wrongCode_showsError() {
        authClient.verifyOtpError = InvalidOtpException()
        val vm = viewModel()
        vm.onPhoneNumberChanged("+919876543210")
        vm.onRequestOtp()
        vm.onOtpChanged("000000")

        vm.onVerifyOtp()

        assertEquals(LoginError.INVALID_OTP, vm.uiState.value.error)
        assertEquals(LoginStep.OTP, vm.uiState.value.step)
    }

    @Test
    fun verifyOtp_networkFailure_showsNetworkError() {
        authClient.verifyOtpError = RuntimeException("boom")
        val vm = viewModel()
        vm.onPhoneNumberChanged("+919876543210")
        vm.onRequestOtp()
        vm.onOtpChanged("123456")

        vm.onVerifyOtp()

        assertEquals(LoginError.NETWORK, vm.uiState.value.error)
    }

    @Test
    fun editPhoneNumber_returnsToPhoneStep_clearingOtp() {
        val vm = viewModel()
        vm.onPhoneNumberChanged("+919876543210")
        vm.onRequestOtp()
        vm.onOtpChanged("123456")

        vm.onEditPhoneNumber()

        val state = vm.uiState.value
        assertEquals(LoginStep.PHONE, state.step)
        assertEquals("", state.otp)
        assertEquals("+919876543210", state.phoneNumber)
    }
}
