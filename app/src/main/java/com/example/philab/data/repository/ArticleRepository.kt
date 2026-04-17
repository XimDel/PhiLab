package com.example.philab.data.repository

import android.content.Context
import com.example.philab.domain.model.Article
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repositorio encargado de cargar los artículos teóricos desde archivos locales.
 *
 * Lee un archivo JSON ubicado en la carpeta de assets y lo convierte en una
 * lista de objetos [Article].
 */
object ArticleRepository {

    /**
     * Carga la lista de artículos desde el archivo `articles.json`.
     *
     * @param context Contexto de la aplicación necesario para acceder a los assets.
     * @return Lista de artículos disponibles.
     */
    fun loadArticles(context: Context): List<Article> {
        val jsonString = context.assets
            .open("articles.json")
            .bufferedReader()
            .use { it.readText() }

        val listType = object : TypeToken<List<Article>>() {}.type
        return Gson().fromJson(jsonString, listType)
    }
}