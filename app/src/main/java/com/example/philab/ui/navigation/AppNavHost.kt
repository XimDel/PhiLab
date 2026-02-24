//mapa de navegación que conecta esas rutas con sus pantallas y controla a dónde ir cuando el usuario presiona un botón.

package com.example.philab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.philab.ui.home.HomeScreen
import com.example.philab.ui.home.HomeViewModel
import com.example.philab.ui.history.HistoryScreen
import com.example.philab.ui.camera.CameraScreen
import com.example.philab.ui.theory.module.TheoryModuleScreen

@Composable
fun AppNavHost(homeViewModel: HomeViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.THEORY_MODULE) {
            TheoryModuleScreen(
                onBack = { navController.popBackStack() },
                onOpenArticle = { index ->
                    // TODO: después navegar a TheoryArticle con argumento
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenTheory = { navController.navigate(Routes.THEORY_MODULE) },
                onStartExperiment = { navController.navigate(Routes.CAMERA) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.CAMERA) { CameraScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }

    }
}
