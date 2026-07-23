package org.charged_proton.secondopinion.data.auth

import android.content.Context

/** App-private SharedPreferences persistence for the session token. */
class SharedPreferencesAuthTokenStore(context: Context) : AuthTokenStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun writeToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val PREFS_NAME = "second_opinion_auth"
        const val KEY_TOKEN = "auth_token"
    }
}
