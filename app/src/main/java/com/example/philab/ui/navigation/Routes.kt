package com.example.philab.ui.navigation

/**
 * Objeto que centraliza las rutas de navegación de la aplicación.
 *
 * Define los identificadores utilizados en el sistema de navegación,
 * así como funciones auxiliares para construir rutas dinámicas
 * con parámetros.
 */
object Routes {

    /**
     * Ruta de la pantalla principal.
     */
    const val HOME = "home"

    /**
     * Ruta del módulo teórico.
     */
    const val THEORY_MODULE = "theory_module"

    /**
     * Ruta base para la visualización de un artículo con parámetro dinámico.
     */
    const val ARTICLE_ROUTE = "article/{articleId}"

    /**
     * Ruta del módulo de laboratorio.
     */
    const val LAB_MODULE = "lab_module"

    /**
     * Ruta del módulo de consejos previos.
     */
    const val TIPS_MODULE = "tips_module"

    /**
     * Ruta para la pantalla de permisos de cámara.
     */
    const val CAMERA_PERMISSION = "camera_permission"

    /**
     * Ruta de la pantalla de cámara.
     */
    const val CAMERA = "camera"

    /**
     * Ruta de la pantalla de resultados.
     */
    const val RESULTS = "results"

    /**
     * Ruta base para el historial de resultados con parámetro dinámico.
     */
    const val RESULTS_HISTORY = "results_history/{sessionId}"

    /**
     * Ruta de la pantalla de historial.
     */
    const val HISTORY = "history"

    /**
     * Ruta para el generador de marcadores ArUco.
     */
    const val ARUCO_GENERATOR = "aruco_generator"

    /**
     * Ruta de la sección de preguntas frecuentes.
     */
    const val FAQ_ROUTE = "faqs"

    /**
     * Ruta para la pantalla de dibujo de marcadores ArUco.
     */
    const val DRAW_ARUCO = "draw_aruco"

    /**
     * Construye la ruta completa para un artículo específico.
     *
     * @param articleId Identificador del artículo.
     * @return Ruta lista para navegación.
     */
    fun articleRoute(articleId: String) = "article/$articleId"

    /**
     * Construye la ruta completa para el historial de una sesión específica.
     *
     * @param sessionId Identificador de la sesión.
     * @return Ruta lista para navegación.
     */
    fun resultsHistoryRoute(sessionId: Long) = "results_history/$sessionId"
}