package org.charged_proton.secondopinion

import android.app.Application
import org.charged_proton.secondopinion.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SecondOpinionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SecondOpinionApp)
            modules(appModule)
        }
    }
}
