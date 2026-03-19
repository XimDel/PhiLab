package com.example.philab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.philab.ui.camera.CameraPermissionScreen
import com.example.philab.ui.lab.experiment.camera.CameraViewModel
import com.example.philab.ui.history.HistoryScreen
import com.example.philab.ui.home.HomeScreen
import com.example.philab.ui.lab.experiment.camera.CameraScreen
import com.example.philab.ui.lab.experiment.tips.PreExperimentTipsScreen
import com.example.philab.ui.lab.menu.LabModuleScreen
import com.example.philab.ui.history.ResultsScreen
import com.example.philab.ui.theory.article.ArticleScreen
import com.example.philab.ui.theory.module.TheoryModuleScreen
import com.example.philab.ui.lab.arucogenerator.ArucoGeneratorScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val cameraViewModel: CameraViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenTheory = { navController.navigate(Routes.THEORY_MODULE) },
                onOpenLab = { navController.navigate(Routes.LAB_MODULE) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }

        composable(Routes.THEORY_MODULE) {
            TheoryModuleScreen(
                onBack = { navController.popBackStack() },
                onOpenArticle = { articleId ->
                    navController.navigate(Routes.articleRoute(articleId))
                }
            )
        }

        composable(
            route = Routes.ARTICLE_ROUTE,
            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
            ArticleScreen(
                articleId = articleId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LAB_MODULE) {
            LabModuleScreen(
                onBack = { navController.popBackStack() },
                onStartExperiment = { navController.navigate(Routes.TIPS_MODULE) },
                onHowItWorks = {
                    // TODO
                },
                onOpenArucoGenerator = { navController.navigate(Routes.ARUCO_GENERATOR) }
            )
        }

        composable(Routes.TIPS_MODULE) {
            PreExperimentTipsScreen(
                onBack = { navController.popBackStack() },
                onStartExperiment = { navController.navigate(Routes.CAMERA_PERMISSION) }
            )
        }

        composable(Routes.CAMERA_PERMISSION) {
            CameraPermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.CAMERA_PERMISSION) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onNavigateToResults = {
                    navController.navigate(Routes.RESULTS)
                },
                viewModel = cameraViewModel
            )
        }

        composable(Routes.RESULTS) {
            val results = cameraViewModel.experimentResults
            if (results != null) {
                ResultsScreen(
                    results = results,
                    onBack = { navController.popBackStack() },
                    onExport = { },
                    onNavigateHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            } else {
                navController.popBackStack()
            }
        }

        composable(Routes.ARUCO_GENERATOR) {
            ArucoGeneratorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) { HistoryScreen() }
    }
}