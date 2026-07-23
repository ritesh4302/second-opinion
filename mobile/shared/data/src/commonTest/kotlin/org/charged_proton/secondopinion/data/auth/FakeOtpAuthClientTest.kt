package org.charged_proton.secondopinion.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.auth.InvalidOtpException
import org.charged_proton.secondopinion.domain.auth.InvalidPhoneNumberException

class FakeOtpAuthClientTest {

    private class InMemoryAuthTokenStore : AuthTokenStore {
        var token: String? = null
        override fun readToken(): String? = token
        override fun writeToken(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private val tokenStore = InMemoryAuthTokenStore()

    @Test
    fun startsSignedOut_whenNoStoredToken() {
        val client = FakeOtpAuthClient(tokenStore)

        assertEquals(AuthState.SignedOut, client.authState.value)
    }

    @Test
    fun restoresSession_fromStoredToken() = runTest {
        tokenStore.token = "fake:fake-919876543210:+919876543210"

        val client = FakeOtpAuthClient(tokenStore)

        assertEquals(
            AuthState.SignedIn(AuthUser("fake-919876543210", "+919876543210")),
            client.authState.value,
        )
        assertEquals("fake:fake-919876543210:+919876543210", client.currentToken())
    }

    @Test
    fun startsSignedOut_whenStoredTokenIsMalformed() {
        tokenStore.token = "not-a-fake-token"

        assertEquals(AuthState.SignedOut, FakeOtpAuthClient(tokenStore).authState.value)
    }

    @Test
    fun requestOtp_rejectsNonE164PhoneNumber() = runTest {
        val client = FakeOtpAuthClient(tokenStore)

        val result = client.requestOtp("9876543210")

        assertIs<InvalidPhoneNumberException>(result.exceptionOrNull())
    }

    @Test
    fun verifyOtp_failsWithoutOtpRequest() = runTest {
        val client = FakeOtpAuthClient(tokenStore)

        val result = client.verifyOtp(FakeOtpAuthClient.FAKE_OTP)

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun verifyOtp_rejectsWrongCode() = runTest {
        val client = FakeOtpAuthClient(tokenStore)
        client.requestOtp("+919876543210")

        val result = client.verifyOtp("000000")

        assertIs<InvalidOtpException>(result.exceptionOrNull())
        assertEquals(AuthState.SignedOut, client.authState.value)
    }

    @Test
    fun signInFlow_persistsTokenAndSignsIn() = runTest {
        val client = FakeOtpAuthClient(tokenStore)
        assertTrue(client.requestOtp("+919876543210").isSuccess)

        val result = client.verifyOtp(FakeOtpAuthClient.FAKE_OTP)

        val user = AuthUser("fake-919876543210", "+919876543210")
        assertEquals(user, result.getOrNull())
        assertEquals(AuthState.SignedIn(user), client.authState.value)
        assertEquals("fake:fake-919876543210:+919876543210", tokenStore.token)
        assertEquals("fake:fake-919876543210:+919876543210", client.currentToken())
    }

    @Test
    fun signOut_clearsTokenAndState() = runTest {
        val client = FakeOtpAuthClient(tokenStore)
        client.requestOtp("+919876543210")
        client.verifyOtp(FakeOtpAuthClient.FAKE_OTP)

        client.signOut()

        assertEquals(AuthState.SignedOut, client.authState.value)
        assertNull(tokenStore.token)
        assertNull(client.currentToken())
    }
}
