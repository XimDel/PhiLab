//Define qué acciones puede hacer el usuario en esta pantalla.

package com.example.philabtest.ui.home

sealed interface HomeEvent {
    data object StartExperiment : HomeEvent
    data object OpenHistory : HomeEvent
}
