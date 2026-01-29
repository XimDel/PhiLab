package com.example.philabtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.philabtest.ui.home.HomeScreen
import com.example.philabtest.ui.home.HomeViewModel
import com.example.philabtest.ui.theme.PhiLabTestTheme

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhiLabTestTheme {
                HomeScreen(
                    viewModel = homeViewModel,
                    onStartExperiment = { /* TODO: ir a pantalla cámara */ },
                    onOpenHistory = { /* TODO: ir a historial */ }
                )
            }
        }
    }
}