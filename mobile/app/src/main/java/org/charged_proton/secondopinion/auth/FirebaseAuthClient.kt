package org.charged_proton.secondopinion.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.charged_proton.secondopinion.domain.auth.AuthClient
import org.charged_proton.secondopinion.domain.auth.AuthState
import org.charged_proton.secondopinion.domain.auth.AuthUser
import org.charged_proton.secondopinion.domain.auth.SignInCancelledException

/**
 * Production [AuthClient]: Credential Manager (Google account picker) →
 * Firebase Auth SDK (`signInWithCredential`) → Firebase ID token as the
 * backend bearer (verified server-side against `securetoken.google.com/<project>`,
 * BACKEND.md §4). Firebase Auth persists the session itself, so no
 * `AuthTokenStore` is involved; [currentToken] returns the cached ID token,
 * transparently refreshed by the SDK when it nears expiry.
 *
 * Requires the Firebase project config (google-services.json) — `AppModule`
 * falls back to `FakeGoogleAuthClient` when no `FirebaseApp` is initialised.
 */
class FirebaseAuthClient(
    private val context: Context,
    private val activityTracker: CurrentActivityTracker,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthClient {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Fires immediately with the persisted session (or null), then on
        // every sign-in/sign-out — the app-level auth gate observes this.
        firebaseAuth.addAuthStateListener { auth ->
            _authState.value = auth.currentUser
                ?.let { AuthState.SignedIn(it.toAuthUser()) }
                ?: AuthState.SignedOut
        }
    }

    override suspend fun signIn(): Result<AuthUser> {
        val activity = activityTracker.currentActivity()
            ?: return Result.failure(IllegalStateException("No resumed Activity to host the sign-in UI"))
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId())
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseUser = firebaseAuth
                .signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
                .await()
                .user
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user"))
            Result.success(firebaseUser.toAuthUser())
        } catch (e: GetCredentialCancellationException) {
            Result.failure(SignInCancelledException())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun currentToken(): String? {
        val user = firebaseAuth.currentUser ?: return null
        // getIdToken can fail offline once the cached token expires; a null
        // bearer then simply yields an unauthenticated backend call.
        return runCatching { user.getIdToken(false).await().token }.getOrNull()
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        // Also clear Credential Manager state so the next sign-in re-shows
        // the account picker instead of silently reusing the last account.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * The Web OAuth client id generated into resources by the google-services
     * plugin. Looked up by name (not `R.string`) because the plugin is applied
     * only when google-services.json exists — a compile-time reference would
     * break the build before the Firebase project is provisioned.
     */
    @SuppressLint("DiscouragedApi")
    private fun serverClientId(): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        check(id != 0) { "default_web_client_id missing — is google-services.json in place?" }
        return context.getString(id)
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
    )
}
