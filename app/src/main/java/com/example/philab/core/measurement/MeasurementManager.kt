package com.example.philab.core.measurement

import com.example.philab.core.camera.UiDetection
import kotlin.math.abs

/**
 * Calcula las dimensiones físicas de un objeto detectado a partir de su
 * bounding box en píxeles y el factor de calibración espacial.
 *
 * @param minBoxSizePx Tamaño mínimo en píxeles (ancho y alto) que debe tener
 * el bounding box para que la medición sea considerada válida.
 * Por defecto `2f`.
 */
class MeasurementManager(
    private val minBoxSizePx: Float = 2f
) {

    /**
     * Mide las dimensiones de un objeto detectado en píxeles, centímetros y metros.
     *
     * Devuelve `null` si el factor de escala no es válido, si las dimensiones
     * del bounding box no son finitas o si el box es demasiado pequeño según
     * [minBoxSizePx].
     *
     * @param detection Detección de UI con las coordenadas del bounding box en píxeles.
     * @param cmPerPx   Factor de conversión píxel → centímetro obtenido de la calibración ArUco.
     * @return [MeasurementResult] con las dimensiones calculadas, o `null` si la medición
     * no es posible.
     */
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