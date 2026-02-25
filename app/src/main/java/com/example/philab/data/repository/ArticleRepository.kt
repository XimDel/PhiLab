package com.example.philab.data.repository

import android.content.Context
import com.example.philab.domain.model.Article
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ArticleRepository {

    fun loadArticles(context: Context): List<Article> {
        val jsonString = context.assets
            .open("articles.json")
            .bufferedReader()
            .use { it.readText() }

        val listType = object : TypeToken<List<Article>>() {}.type
        return Gson().fromJson(jsonString, listType)
    }
}