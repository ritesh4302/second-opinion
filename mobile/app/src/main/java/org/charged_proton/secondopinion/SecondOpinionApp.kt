package org.charged_proton.secondopinion

import android.app.Application
import org.charged_proton.secondopinion.auth.CurrentActivityTracker
import org.charged_proton.secondopinion.di.appModule
import org.charged_proton.secondopinion.telemetry.createAppTelemetry
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SecondOpinionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val telemetry = createAppTelemetry(this).also { it.setCollectionEnabled(false) }
        val koin = startKoin {
            androidContext(this@SecondOpinionApp)
            modules(appModule(telemetry))
        }.koin
        // Feeds FirebaseAuthClient the resumed Activity that Credential
        // Manager needs to show the Google account picker.
        registerActivityLifecycleCallbacks(koin.get<CurrentActivityTracker>())
    }
}
