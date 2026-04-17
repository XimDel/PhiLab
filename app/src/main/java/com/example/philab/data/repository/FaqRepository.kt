package com.example.philab.data.repository

import android.content.Context
import com.example.philab.domain.model.Faq
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repositorio encargado de cargar las preguntas frecuentes (FAQ)
 * desde un archivo JSON en los assets.
 */
object FaqRepository {

    /**
     * Carga la lista de FAQs desde el archivo `faqs.json`.
     *
     * @param context Contexto de la aplicación necesario para acceder a los assets.
     * @return Lista de objetos [Faq].
     */
    fun loadFaqs(context: Context): List<Faq> {
        val json = context.assets.open("faqs.json")
            .bufferedReader()
            .use { it.readText() }

        return Gson().fromJson(
            json,
            object : TypeToken<List<Faq>>() {}.type
        )
    }
}