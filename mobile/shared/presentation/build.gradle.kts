plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    androidLibrary {
        namespace = "org.charged_proton.secondopinion.shared.presentation"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
