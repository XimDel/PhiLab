package com.example.philab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.philab.ui.navigation.AppNavHost
import com.example.philab.ui.theme.PhiLabTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhiLabTheme {
                AppNavHost()
            }
        }
    }
}