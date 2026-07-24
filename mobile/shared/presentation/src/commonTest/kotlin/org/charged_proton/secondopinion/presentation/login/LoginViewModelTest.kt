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
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import org.charged_proton.secondopinion.domain.usecase.SignInUseCase
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

    private fun viewModel() = LoginViewModel(SignInUseCase(authClient))

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
}
