package com.example.philab.ui.navigation

object Routes {
    const val HOME             = "home"
    const val THEORY_MODULE    = "theory_module"
    const val ARTICLE_ROUTE    = "article/{articleId}"
    const val LAB_MODULE       = "lab_module"
    const val TIPS_MODULE      = "tips_module"
    const val CAMERA_PERMISSION = "camera_permission"
    const val CAMERA           = "camera"
    const val RESULTS          = "results"
    const val RESULTS_HISTORY  = "results_history/{sessionId}"
    const val HISTORY          = "history"
    const val ARUCO_GENERATOR  = "aruco_generator"

    fun articleRoute(articleId: String) = "article/$articleId"
    fun resultsHistoryRoute(sessionId: Long) = "results_history/$sessionId"
}