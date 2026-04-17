package com.example.philab.ui.lab.faqs

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.example.philab.data.repository.FaqRepository.loadFaqs
import com.example.philab.domain.model.Faq

/**
 * ViewModel encargado de gestionar los datos de preguntas frecuentes (FAQ).
 *
 * Carga la lista de FAQs desde el repositorio y la expone como estado observable
 * para ser consumido por la interfaz de usuario.
 *
 * @param application Contexto de la aplicación necesario para acceder a recursos.
 */
class FaqViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Lista de preguntas frecuentes disponible para la UI.
     */
    var faqList by mutableStateOf<List<Faq>>(emptyList())
        private set

    init {
        load()
    }

    /**
     * Carga la lista de FAQs desde el repositorio.
     */
    private fun load() {
        faqList = loadFaqs(getApplication())
    }
}