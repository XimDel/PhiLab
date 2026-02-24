package com.example.philab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.philab.ui.home.HomeViewModel
import com.example.philab.ui.navigation.AppNavHost
import com.example.philab.ui.theme.PhiLabTestTheme

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhiLabTestTheme {
                AppNavHost(homeViewModel = homeViewModel)
            }
        }
    }
}
