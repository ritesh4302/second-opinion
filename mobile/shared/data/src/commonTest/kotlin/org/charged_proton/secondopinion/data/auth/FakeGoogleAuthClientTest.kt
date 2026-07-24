package org.charged_proton.secondopinion.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser

class FakeGoogleAuthClientTest {

    private class InMemoryAuthTokenStore : AuthTokenStore {
        var token: String? = null
        override fun readToken(): String? = token
        override fun writeToken(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private val tokenStore = InMemoryAuthTokenStore()

    private val fakeUser = AuthUser(
        uid = FakeGoogleAuthClient.FAKE_UID,
        email = FakeGoogleAuthClient.FAKE_EMAIL,
        displayName = FakeGoogleAuthClient.FAKE_DISPLAY_NAME,
    )

    private val fakeToken =
        "fake:${fakeUser.uid}:${fakeUser.email}:${fakeUser.displayName}"

    @Test
    fun startsSignedOut_whenNoStoredToken() {
        val client = FakeGoogleAuthClient(tokenStore)

        assertEquals(AuthState.SignedOut, client.authState.value)
    }

    @Test
    fun restoresSession_fromStoredToken() = runTest {
        tokenStore.token = fakeToken

        val client = FakeGoogleAuthClient(tokenStore)

        assertEquals(AuthState.SignedIn(fakeUser), client.authState.value)
        assertEquals(fakeToken, client.currentToken())
    }

    @Test
    fun startsSignedOut_whenStoredTokenIsMalformed() {
        tokenStore.token = "not-a-fake-token"

        assertEquals(AuthState.SignedOut, FakeGoogleAuthClient(tokenStore).authState.value)
    }

    @Test
    fun signIn_persistsTokenAndSignsIn() = runTest {
        val client = FakeGoogleAuthClient(tokenStore)

        val result = client.signIn()

        assertEquals(fakeUser, result.getOrNull())
        assertEquals(AuthState.SignedIn(fakeUser), client.authState.value)
        assertEquals(fakeToken, tokenStore.token)
        assertEquals(fakeToken, client.currentToken())
    }

    @Test
    fun signInWithEmail_persistsTokenAndSignsIn() = runTest {
        val client = FakeGoogleAuthClient(tokenStore)

        val result = client.signInWithEmail("jane@pharmacy.org", "secret")

        val expectedUser = AuthUser(
            uid = FakeGoogleAuthClient.FAKE_EMAIL_UID,
            email = "jane@pharmacy.org",
            displayName = "jane",
        )
        assertEquals(expectedUser, result.getOrNull())
        assertEquals(AuthState.SignedIn(expectedUser), client.authState.value)
        assertEquals("fake:${expectedUser.uid}:jane@pharmacy.org:jane", tokenStore.token)
    }

    @Test
    fun signUpWithEmail_behavesLikeEmailSignIn() = runTest {
        val client = FakeGoogleAuthClient(tokenStore)

        val result = client.signUpWithEmail("jane@pharmacy.org", "secret")

        assertEquals(FakeGoogleAuthClient.FAKE_EMAIL_UID, result.getOrNull()?.uid)
        assertEquals(AuthState.SignedIn(result.getOrNull()!!), client.authState.value)
    }

    @Test
    fun signOut_clearsTokenAndState() = runTest {
        val client = FakeGoogleAuthClient(tokenStore)
        client.signIn()

        client.signOut()

        assertEquals(AuthState.SignedOut, client.authState.value)
        assertNull(tokenStore.token)
        assertNull(client.currentToken())
    }
}
