package com.example.philab.data.repository

import android.content.Context
import com.example.philab.domain.model.Faq
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FaqRepository {

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



