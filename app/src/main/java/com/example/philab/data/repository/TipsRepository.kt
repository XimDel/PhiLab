package com.example.philab.data.repository

import android.content.Context

/**
 * Repositorio encargado de cargar el contenido de recomendaciones previas
 * desde un archivo de texto en los assets.
 */
object TipsRepository {

    /**
     * Lee el archivo `tips.txt` y devuelve su contenido como texto.
     *
     * @param context Contexto de la aplicación necesario para acceder a los assets.
     * @return Cadena de texto con las recomendaciones.
     */
    fun loadTips(context: Context): String {
        return context.assets.open("tips.txt")
            .bufferedReader()
            .use { it.readText() }
    }
}