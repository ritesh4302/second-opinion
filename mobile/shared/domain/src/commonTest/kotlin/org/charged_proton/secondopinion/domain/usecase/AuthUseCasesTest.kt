package org.charged_proton.secondopinion.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.auth.InvalidPhoneNumberException
import org.charged_proton.secondopinion.domain.testutil.FakeAuthClient

class AuthUseCasesTest {

    private val authClient = FakeAuthClient()

    @Test
    fun observeAuthState_reflectsClientState() {
        val observe = ObserveAuthStateUseCase(authClient)

        assertEquals(AuthState.SignedOut, observe().value)
    }

    @Test
    fun requestOtp_delegatesPhoneNumber() = runTest {
        val result = RequestOtpUseCase(authClient)("+919876543210")

        assertTrue(result.isSuccess)
        assertEquals(listOf("+919876543210"), authClient.requestedPhoneNumbers)
    }

    @Test
    fun requestOtp_propagatesFailure() = runTest {
        authClient.requestOtpError = InvalidPhoneNumberException("12345")

        val result = RequestOtpUseCase(authClient)("12345")

        assertIs<InvalidPhoneNumberException>(result.exceptionOrNull())
    }

    @Test
    fun verifyOtp_signsIn() = runTest {
        val result = VerifyOtpUseCase(authClient)("123456")

        assertTrue(result.isSuccess)
        assertEquals(listOf("123456"), authClient.verifiedCodes)
        assertIs<AuthState.SignedIn>(authClient.authState.value)
    }

    @Test
    fun verifyOtp_propagatesFailure() = runTest {
        authClient.verifyOtpError = InvalidOtpException()

        val result = VerifyOtpUseCase(authClient)("000000")

        assertIs<InvalidOtpException>(result.exceptionOrNull())
        assertEquals(AuthState.SignedOut, authClient.authState.value)
    }
}
