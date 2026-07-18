package org.charged_proton.secondopinion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.charged_proton.secondopinion.ui.assessment.AssessmentScreen
import org.charged_proton.secondopinion.ui.history.HistoryScreen
import org.charged_proton.secondopinion.ui.record.RecordScreen

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
) {
    NavHost(
        navController = navController,
        startDestination = Routes.RECORD,
        modifier = modifier,
    ) {
        composable(Routes.RECORD) {
            RecordScreen(
                onOpenAssessment = { caseId -> navController.navigate(Routes.assessment(caseId)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
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
            AssessmentScreen(caseId = caseId)
        }
    }
}
