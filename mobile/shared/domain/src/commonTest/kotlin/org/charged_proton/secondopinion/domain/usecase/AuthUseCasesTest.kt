package org.charged_proton.secondopinion.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.EmailAuthError
import org.charged_proton.secondopinion.domain.auth.EmailAuthException
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException
import org.charged_proton.secondopinion.domain.testutil.FakeAuthClient

class AuthUseCasesTest {

    private val authClient = FakeAuthClient()

    @Test
    fun observeAuthState_reflectsClientState() {
        val observe = ObserveAuthStateUseCase(authClient)

        assertEquals(AuthState.SignedOut, observe().value)
    }

    @Test
    fun signIn_signsIn() = runTest {
        val result = SignInUseCase(authClient)()

        assertTrue(result.isSuccess)
        assertEquals(1, authClient.signInCalls)
        assertIs<AuthState.SignedIn>(authClient.authState.value)
    }

    @Test
    fun signIn_propagatesFailure() = runTest {
        authClient.signInError = SignInCancelledException()

        val result = SignInUseCase(authClient)()

        assertIs<SignInCancelledException>(result.exceptionOrNull())
        assertEquals(AuthState.SignedOut, authClient.authState.value)
    }

    @Test
    fun signInWithEmail_trimsEmail_andSignsIn() = runTest {
        val result = SignInWithEmailUseCase(authClient)(" jane@pharmacy.org ", "secret")

        assertTrue(result.isSuccess)
        assertEquals(1, authClient.emailSignInCalls)
        assertEquals("jane@pharmacy.org", authClient.lastEmail)
        assertEquals("secret", authClient.lastPassword)
        assertIs<AuthState.SignedIn>(authClient.authState.value)
    }

    @Test
    fun signUpWithEmail_trimsEmail_andSignsIn() = runTest {
        val result = SignUpWithEmailUseCase(authClient)(" jane@pharmacy.org ", "secret")

        assertTrue(result.isSuccess)
        assertEquals(1, authClient.emailSignUpCalls)
        assertEquals("jane@pharmacy.org", authClient.lastEmail)
        assertIs<AuthState.SignedIn>(authClient.authState.value)
    }

    @Test
    fun signInWithEmail_propagatesFailure() = runTest {
        authClient.signInError = EmailAuthException(EmailAuthError.INVALID_CREDENTIALS)

        val result = SignInWithEmailUseCase(authClient)("jane@pharmacy.org", "wrong")

        assertEquals(
            EmailAuthError.INVALID_CREDENTIALS,
            assertIs<EmailAuthException>(result.exceptionOrNull()).error,
        )
        assertEquals(AuthState.SignedOut, authClient.authState.value)
    }
}
