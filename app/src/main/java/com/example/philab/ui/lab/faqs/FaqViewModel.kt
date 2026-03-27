package com.example.philab.ui.lab.faqs

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.example.philab.data.repository.FaqRepository.loadFaqs
import com.example.philab.domain.model.Faq

class FaqViewModel(application: Application) : AndroidViewModel(application) {

    var faqList by mutableStateOf<List<Faq>>(emptyList())
        private set

    init {
        load()
    }

    private fun load() {
        faqList = loadFaqs(getApplication())
    }
}