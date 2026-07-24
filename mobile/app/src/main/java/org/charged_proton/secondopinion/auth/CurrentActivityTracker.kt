package org.charged_proton.secondopinion.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the resumed [Activity] so headless collaborators (Koin singletons
 * like `FirebaseAuthClient`) can launch UI flows that require an Activity —
 * Credential Manager's account picker rejects a bare application context.
 * Registered via [Application.registerActivityLifecycleCallbacks] in
 * `SecondOpinionApp`; holds only a [WeakReference] to avoid leaking.
 */
class CurrentActivityTracker : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    fun currentActivity(): Activity? = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
