import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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

    // Umbrella framework for the iOS app: exports domain models/use cases and
    // the data implementations alongside the ViewModels, so Xcode consumes a
    // single SharedKit.xcframework (built with :shared:presentation:assembleSharedKitXCFramework).
    val xcf = XCFramework("SharedKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SharedKit"
            isStatic = true
            export(project(":shared:domain"))
            export(project(":shared:data"))
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
        }
        // api so the framework can export the data module (iOS only; on
        // Android the app module wires :shared:data itself).
        iosMain.dependencies {
            api(project(":shared:data"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
