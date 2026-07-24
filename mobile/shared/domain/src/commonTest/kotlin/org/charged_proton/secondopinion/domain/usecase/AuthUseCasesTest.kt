package org.charged_proton.secondopinion.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.auth.AuthState
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
}
