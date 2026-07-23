package org.charged_proton.secondopinion.data.auth

/**
 * Persists the backend bearer token across app restarts. Android adapter:
 * [SharedPreferencesAuthTokenStore] (androidMain).
 */
interface AuthTokenStore {
    fun readToken(): String?
    fun writeToken(token: String)
    fun clear()
}
