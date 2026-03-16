package com.example.philab.core.measurement

import com.example.philab.core.camera.UiDetection
import kotlin.math.abs

class MeasurementManager(
    private val minBoxSizePx: Float = 2f
) {

    fun measureObject(
        detection: UiDetection,
        cmPerPx: Float
    ): MeasurementResult? {
        if (cmPerPx <= 0f || !cmPerPx.isFinite()) return null

        val widthPx = abs(detection.right - detection.left)
        val heightPx = abs(detection.bottom - detection.top)

        if (!widthPx.isFinite() || !heightPx.isFinite()) return null
        if (widthPx < minBoxSizePx || heightPx < minBoxSizePx) return null

        val widthCm = widthPx * cmPerPx
        val heightCm = heightPx * cmPerPx

        if (!widthCm.isFinite() || !heightCm.isFinite()) return null

        val centerX = (detection.left + detection.right) / 2f
        val centerY = (detection.top + detection.bottom) / 2f

        return MeasurementResult(
            label = detection.label,
            widthPx = widthPx,
            heightPx = heightPx,
            widthCm = widthCm,
            heightCm = heightCm,
            widthM = widthCm / 100f,
            heightM = heightCm / 100f,
            centerX = centerX,
            centerY = centerY
        )
    }
}