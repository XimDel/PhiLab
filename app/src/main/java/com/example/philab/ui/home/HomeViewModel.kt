//Es el intermediario entre la UI y la lógica.
//Recibe los eventos (HomeEvent)
//Mantiene el estado (HomeUiState)
//Decide qué cambia cuando el usuario hace algo.

package com.example.philab.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.StartExperiment -> {
                // Cambiar navegacion
            }
            HomeEvent.OpenHistory -> {
                // Cambiar navegacion
            }
            HomeEvent.OpenTheory -> {
                // Cambiar navegacion
            }
        }
    }
}
