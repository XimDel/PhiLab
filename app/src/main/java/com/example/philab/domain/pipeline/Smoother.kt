package com.example.philab.domain.pipeline

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3: SMOOTHING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Suaviza la señal x(t) usando una Media Móvil Ponderada Triangularmente
 * (LWMA — Linearly Weighted Moving Average).
 *
 * POR QUÉ LWMA Y NO OTROS MÉTODOS:
 *
 * • Media móvil simple (SMA): da igual peso a todos los puntos → introduce
 *   retraso de fase proporcional a la ventana. Malo para cinemática.
 *
 * • Gaussian / Savitzky-Golay: excelente pero complejo de implementar sin
 *   librerías. SG puede calcular también las derivadas directamente.
 *
 * • LWMA triangular: peso lineal decreciente desde el centro hacia los bordes.
 *   Preserva mejor los picos (menor retraso que SMA) y es O(n·w) — eficiente.
 *   Para señales de laboratorio de <1000 puntos es más que suficiente.
 *
 * • EMA (Exponential Moving Average): solo mira hacia atrás → introduce
 *   retraso sistemático. No apropiado para post-procesamiento offline.
 *
 * La LWMA con múltiples pasadas converge hacia un perfil gaussiano
 * (central limit theorem), lo que da un resultado similar a Gaussian
 * pero con implementación Kotlin pura.
 *
 * PRESERVACIÓN DE BORDES:
 * En los extremos (primeros/últimos w/2 puntos) se usa una ventana reducida
 * simétricamente para no introducir bias hacia el interior del dataset.
 */
object Smoother {

    /**
     * Aplica smoothing a la señal x(t) de la lista de CleanPoints.
     * y(t) se suaviza de forma independiente.
     * Los timestamps NO se modifican.
     */
    fun smooth(
        points: List<CleanPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        if (points.size < 3) return points

        var xValues = points.map { it.x }
        var yValues = points.map { it.y }

        // Múltiples pasadas para mayor suavidad
        repeat(config.smoothingPasses) {
            xValues = lwmaPass(xValues, config.smoothingWindowSize)
            yValues = lwmaPass(yValues, config.smoothingWindowSize)
        }

        return points.mapIndexed { i, cp ->
            cp.copy(x = xValues[i], y = yValues[i])
        }
    }

    // ── Una pasada de LWMA triangular ─────────────────────────────────────────

    private fun lwmaPass(values: List<Float>, windowSize: Int): List<Float> {
        val half = windowSize / 2
        val weights = triangularWeights(half)

        return values.mapIndexed { i, _ ->
            val start = maxOf(0, i - half)
            val end   = minOf(values.lastIndex, i + half)

            var weightedSum = 0f
            var totalWeight = 0f

            for (j in start..end) {
                // Índice dentro del kernel [-half, +half]
                val kernelIndex = j - i + half
                val w = if (kernelIndex in weights.indices) weights[kernelIndex] else 1f
                weightedSum += values[j] * w
                totalWeight += w
            }

            if (totalWeight > 0f) weightedSum / totalWeight else values[i]
        }
    }

    /**
     * Genera pesos triangulares simétricos: [1, 2, ..., half+1, ..., 2, 1]
     * Ejemplo con half=2: [1, 2, 3, 2, 1] → se normaliza al dividir por totalWeight.
     */
    private fun triangularWeights(half: Int): FloatArray {
        val size = 2 * half + 1
        return FloatArray(size) { i ->
            (half + 1 - kotlin.math.abs(i - half)).toFloat()
        }
    }

    /**
     * Variante adaptativa: ajusta el tamaño de la ventana según la densidad
     * temporal local. Útil si el muestreo es muy irregular.
     */
    fun smoothAdaptive(
        points: List<CleanPoint>,
        targetWindowSec: Float = 0.1f,       // ventana temporal deseada (100 ms)
        passes: Int = 2
    ): List<CleanPoint> {
        if (points.size < 3) return points

        // Calcular dt promedio para estimar la ventana en puntos
        val avgDt = if (points.size > 1) {
            (points.last().tSec - points.first().tSec) / (points.size - 1)
        } else {
            0.033f // 30 fps como fallback
        }

        val windowSize = maxOf(3, ((targetWindowSec / avgDt).toInt() / 2) * 2 + 1)
        val config = PipelineConfig(
            smoothingWindowSize = windowSize,
            smoothingPasses = passes
        )

        return smooth(points, config)
    }
}