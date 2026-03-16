package com.example.philab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.philab.ui.navigation.AppNavHost
import com.example.philab.ui.theme.PhiLabTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OpenCVLoader.initLocal()

        setContent {
            PhiLabTheme {
                AppNavHost()
            }
        }
    }
}