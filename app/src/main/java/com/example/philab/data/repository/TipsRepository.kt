package com.example.philab.data.repository

import android.content.Context

object TipsRepository {
    fun loadTips(context: Context): String {
        return context.assets.open("tips.txt")
            .bufferedReader()
            .use { it.readText() }
    }
}