package com.example.philab.ui.navigation

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.LocalActivity

/**
 * Composable que permite desbloquear la orientación de la pantalla
 * mientras está activo en la composición.
 *
 * Cambia temporalmente la orientación de la actividad a modo sensor
 * (rotación libre) y, al salir de la composición, restaura la orientación
 * a modo vertical (portrait).
 *
 * Se usa en pantallas como cámara donde se requiere rotación dinámica.
 */
@Composable
fun UnlockOrientation() {
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}