package com.example.philab.core.measurement

data class MeasurementResult(
    val label: String,
    val widthPx: Float,
    val heightPx: Float,
    val widthCm: Float,
    val heightCm: Float,
    val widthM: Float,
    val heightM: Float,
    val centerX: Float,
    val centerY: Float
)