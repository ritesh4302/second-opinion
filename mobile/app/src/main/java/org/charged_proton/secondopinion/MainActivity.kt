package org.charged_proton.secondopinion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import org.charged_proton.secondopinion.ui.navigation.AuthGate
import org.charged_proton.secondopinion.ui.theme.SecondOpinionTheme
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val telemetry: AppTelemetry by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecondOpinionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthGate(
                        modifier = Modifier.padding(innerPadding),
                        telemetry = telemetry,
                    )
                }
            }
        }
    }
}