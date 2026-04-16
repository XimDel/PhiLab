package com.example.philab.core.calibration

import androidx.compose.ui.geometry.Offset

/**
 * Representa los posibles estados del proceso de calibración mediante marcadores ArUco.
 *
 * Esta clase sellada modela el ciclo de vida completo de la calibración:
 * desde el estado inicial inactivo, pasando por la búsqueda del marcador,
 * hasta la calibración exitosa o un error.
 */
sealed class CalibrationState {

    /**
     * Estado inicial antes de que comience cualquier proceso de calibración.
     */
    data object Idle : CalibrationState()

    /**
     * Estado activo en el que se está buscando un marcador ArUco en el fotograma actual,
     * pero aún no se ha detectado ninguno válido.
     */
    data object Searching : CalibrationState()

    /**
     * Estado que indica que la calibración fue completada exitosamente.
     *
     * @property cmPerPx Factor de escala suavizado expresado en centímetros por píxel.
     * @property markerSizeCm Tamaño físico real del marcador en centímetros.
     * @property markerSizePx Tamaño estimado del marcador en píxeles dentro del fotograma.
     * @property markerId Identificador del marcador ArUco detectado, o `null` si no está disponible.
     * @property corners Lista de las cuatro esquinas del marcador en coordenadas del bitmap.
     * @property bitmapWidth Ancho del bitmap procesado en píxeles.
     * @property bitmapHeight Alto del bitmap procesado en píxeles.
     */
    data class Calibrated(
        val cmPerPx: Float,
        val markerSizeCm: Float,
        val markerSizePx: Float,
        val markerId: Int? = null,
        val corners: List<Offset> = emptyList(),
        val bitmapWidth: Int = 0,
        val bitmapHeight: Int = 0
    ) : CalibrationState()

    /**
     * Estado que indica que ocurrió un error durante el proceso de calibración.
     * @property message Descripción legible del error ocurrido.
     */
    data class Error(
        val message: String
    ) : CalibrationState()
}