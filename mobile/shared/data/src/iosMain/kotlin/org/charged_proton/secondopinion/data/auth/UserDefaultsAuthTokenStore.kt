package org.charged_proton.secondopinion.data.auth

import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults counterpart of the Android SharedPreferences token store.
 * TODO(ios): migrate to the Keychain before wider distribution — defaults
 * are app-private but not encrypted at rest like Keychain items.
 */
class UserDefaultsAuthTokenStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AuthTokenStore {

    override fun readToken(): String? = defaults.stringForKey(KEY)

    override fun writeToken(token: String) {
        defaults.setObject(token, KEY)
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY)
    }

    private companion object {
        const val KEY = "backend_session_token"
    }
}
