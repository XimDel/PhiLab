//nombres únicos de las pantallas (rutas) para navegar.

package com.example.philab.ui.navigation

object Routes {
    const val HOME = "home"
    const val CAMERA_PERMISSION = "camera_permission"

    const val CAMERA = "camera"
    const val HISTORY = "history"
    const val THEORY_MODULE = "theory_module"
    const val LAB_MODULE = "lab_module"
    const val RESULTS = "results"
    const val TIPS_MODULE = "tips_module"
    const val ARUCO_GENERATOR = "aruco_generator"

    const val ARTICLE_ROUTE = "article/{articleId}"

    fun articleRoute(articleId: String) = "article/$articleId"
}
