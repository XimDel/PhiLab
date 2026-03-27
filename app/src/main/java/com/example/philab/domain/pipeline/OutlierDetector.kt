package com.example.philab.domain.pipeline

import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 1: OUTLIER DETECTION
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Detecta outliers usando dos capas, pero con criterio AND (ambas deben rechazar).
 *
 * CAMBIO CRÍTICO vs versión anterior:
 *   Antes: OR  → basta con que UNA capa lo rechace → demasiado agresivo
 *   Ahora: AND → ambas capas deben rechazarlo → más conservador
 *
 * Para datos de tracking de cámara (ruidosos por naturaleza), el criterio OR
 * eliminaba ~25% de los puntos, creando gaps que el GapHandler rellenaba con
 * interpolaciones lineales. Esas interpolaciones generaban velocidades espurias
 * en sus bordes. El criterio AND es mucho más apropiado.
 *
 * CAPA 1 — Velocidad instantánea con MAD:
 *   Solo marca puntos cuya velocidad es anómala respecto al resto.
 *   Con el umbral aumentado a 5.0 (era 4.0), tolera mejor la variabilidad
 *   normal del tracking óptico.
 *
 * CAPA 2 — Ventana local de posición con MAD:
 *   Solo marca puntos muy alejados de su vecindad local.
 *   Con madMultiplier = 5.0 (era 3.5), es menos agresivo.
 */
object OutlierDetector {

    fun detect(
        points: List<RawPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        if (points.size < config.minPointsForStats) {
            return points.map { it.toClean(isValid = true) }
        }

        val velocityOutliers = detectByInstantaneousVelocity(points, config)
        val positionOutliers = detectByLocalPosition(points, config)

        // CAMBIO CRÍTICO: AND en lugar de OR
        // Un punto solo es outlier si AMBAS capas lo rechazan.
        val combined = velocityOutliers.zip(positionOutliers) { v, p ->
            v.copy(isValid = v.isValid || p.isValid)   // OR en validez = AND en rechazo
        }

        return combined
    }

    // ── Capa 1: MAD sobre velocidades instantáneas ────────────────────────────

    private fun detectByInstantaneousVelocity(
        points: List<RawPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        val speeds = mutableListOf<Float>()
        for (i in 1 until points.size) {
            val dt = points[i].tSec - points[i - 1].tSec
            if (dt < config.minDtMs) { speeds.add(0f); continue }
            val dx = points[i].x - points[i - 1].x
            speeds.add(abs(dx / dt))
        }

        if (speeds.isEmpty()) return points.map { it.toClean(isValid = true) }

        val medianSpeed = median(speeds)
        val mad         = mad(speeds, medianSpeed)

        // Umbral más generoso: velocityMadMultiplier ahora debería ser 5.0
        val threshold   = medianSpeed + config.velocityMadMultiplier * mad

        val outlierSet  = mutableSetOf<Int>()
        for (i in speeds.indices) {
            if (speeds[i] > threshold) {
                outlierSet.add(i)
                outlierSet.add(i + 1)
            }
        }

        return points.mapIndexed { index, rawPoint ->
            rawPoint.toClean(isValid = index !in outlierSet)
        }
    }

    // ── Capa 2: MAD local de posición ────────────────────────────────────────

    private fun detectByLocalPosition(
        points: List<RawPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        val half   = config.windowSize / 2
        val result = mutableListOf<CleanPoint>()

        for (i in points.indices) {
            val windowStart = maxOf(0, i - half)
            val windowEnd   = minOf(points.size - 1, i + half)
            val windowX     = (windowStart..windowEnd).map { points[it].x }

            if (windowX.size < config.minPointsForStats) {
                result.add(points[i].toClean(isValid = true))
                continue
            }

            val localMedian  = median(windowX)
            val localMad     = mad(windowX, localMedian)
            val effectiveMad = if (localMad < 1e-6f) estimateMinimumMad(points) else localMad
            val deviation    = abs(points[i].x - localMedian)

            // madMultiplier ahora debería ser 5.0 en PipelineConfig
            result.add(points[i].toClean(isValid = deviation <= config.madMultiplier * effectiveMad))
        }

        return result
    }

    // ── Utilidades estadísticas ───────────────────────────────────────────────

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid    = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f
        else sorted[mid]
    }

    fun mad(values: List<Float>, med: Float): Float {
        val deviations = values.map { abs(it - med) }
        return 1.4826f * median(deviations)
    }

    private fun estimateMinimumMad(points: List<RawPoint>): Float {
        val xs    = points.map { it.x }
        val range = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        return maxOf(range * 0.02f, 0.5f)
    }

    private fun RawPoint.toClean(isValid: Boolean) =
        CleanPoint(tSec = tSec, x = x, y = y, isValid = isValid)
}