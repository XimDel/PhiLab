package com.example.philab.core.calibration

import androidx.compose.ui.geometry.Offset

sealed class CalibrationState {

    data object Idle : CalibrationState()

    data object Searching : CalibrationState()

    data class Calibrated(
        val cmPerPx: Float,
        val markerSizeCm: Float,
        val markerSizePx: Float,
        val markerId: Int? = null,
        val corners: List<Offset> = emptyList(),
        val bitmapWidth: Int = 0,
        val bitmapHeight: Int = 0
    ) : CalibrationState()

    data class Error(
        val message: String
    ) : CalibrationState()
}