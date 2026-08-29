package org.charged_proton.secondopinion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.charged_proton.secondopinion.ui.assessment.AssessmentScreen
import org.charged_proton.secondopinion.ui.history.HistoryScreen
import org.charged_proton.secondopinion.ui.record.RecordScreen
import org.charged_proton.secondopinion.telemetry.AppTelemetry
import org.charged_proton.secondopinion.telemetry.NoOpAppTelemetry
import org.charged_proton.secondopinion.telemetry.TelemetryScreen

object Routes {
    const val RECORD = "record"
    const val HISTORY = "history"
    const val ASSESSMENT = "assessment/{caseId}"

    fun assessment(caseId: String) = "assessment/$caseId"
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    telemetry: AppTelemetry = NoOpAppTelemetry,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry?.destination?.route) {
        val screen = when (backStackEntry?.destination?.route) {
            Routes.RECORD -> TelemetryScreen.RECORD
            Routes.HISTORY -> TelemetryScreen.HISTORY
            Routes.ASSESSMENT -> TelemetryScreen.ASSESSMENT
            else -> null
        }
        if (screen != null) telemetry.screen(screen)
    }
    NavHost(
        navController = navController,
        startDestination = Routes.RECORD,
        modifier = modifier,
    ) {
        composable(Routes.RECORD) {
            RecordScreen(
                onOpenAssessment = { caseId -> navController.navigate(Routes.assessment(caseId)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                telemetry = telemetry,
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenCase = { caseId -> navController.navigate(Routes.assessment(caseId)) },
            )
        }
        composable(
            route = Routes.ASSESSMENT,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val caseId = requireNotNull(backStackEntry.arguments?.getString("caseId"))
            AssessmentScreen(caseId = caseId, telemetry = telemetry)
        }
    }
}
