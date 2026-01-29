package com.example.philabtest.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.philabtest.ui.theme.PhiLabTestTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartExperiment: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    HomeContent(
        isReady = state.isReady,
        onStartExperiment = {
            viewModel.onEvent(HomeEvent.StartExperiment)
            onStartExperiment()
        },
        onOpenHistory = {
            viewModel.onEvent(HomeEvent.OpenHistory)
            onOpenHistory()
        }
    )
}

@Composable
private fun HomeContent(
    isReady: Boolean,
    onStartExperiment: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(enabled = isReady, onClick = onStartExperiment) {
            Text("Iniciar experimento")
        }
        Button(onClick = onOpenHistory) {
            Text("Historial")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PhiLabTestTheme {
        HomeContent(
            isReady = true,
            onStartExperiment = {},
            onOpenHistory = {}
        )
    }
}
