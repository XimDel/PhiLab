package com.example.philab.core.camera

data class UiDetection(
    val label: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val isSelected: Boolean = false,
)