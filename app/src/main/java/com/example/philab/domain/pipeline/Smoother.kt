package com.example.philab.domain.pipeline

/**
 * Etapa 3 del pipeline cinemático. Suaviza la señal de posición `x(t)` e `y(t)`
 * usando una Media Móvil Ponderada Triangularmente (LWMA — Linearly Weighted Moving Average).
 *
 * Los pesos decrecen linealmente desde el centro de la ventana hacia los extremos,
 * lo que preserva mejor los picos y reduce el retraso de fase respecto a una media
 * móvil simple (SMA). Con múltiples pasadas, el perfil de pesos converge hacia una
 * forma gaussiana por el teorema central del límite.
 *
 * En los extremos de la señal se usa una ventana reducida simétricamente para
 * no introducir sesgo hacia el interior del dataset. Los timestamps no se modifican.
 */
object Smoother {

    /**
     * Aplica suavizado LWMA a las coordenadas `x` e `y` de cada [CleanPoint].
     *
     * Se ejecutan [PipelineConfig.smoothingPasses] pasadas sucesivas sobre cada
     * dimensión con una ventana de [PipelineConfig.smoothingWindowSize] puntos.
     * Los timestamps y el flag `isValid` de cada punto se conservan intactos.
     *
     * @param points Lista de puntos limpios a suavizar.
     * @param config Parámetros del pipeline con el tamaño de ventana y número de pasadas.
     * @return Lista de [CleanPoint] con las coordenadas suavizadas.
     */
    fun smooth(
        points: List<CleanPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        if (points.size < 3) return points

        var xValues = points.map { it.x }
        var yValues = points.map { it.y }

        repeat(config.smoothingPasses) {
            xValues = lwmaPass(xValues, config.smoothingWindowSize)
            yValues = lwmaPass(yValues, config.smoothingWindowSize)
        }

        return points.mapIndexed { i, cp ->
            cp.copy(x = xValues[i], y = yValues[i])
        }
    }

    /**
     * Ejecuta una pasada de LWMA triangular sobre una lista de valores flotantes.
     *
     * En cada posición se calcula la media ponderada de los puntos dentro de la
     * ventana `[i - half, i + half]`, usando pesos triangulares normalizados.
     *
     * @param values Lista de valores a suavizar.
     * @param windowSize Tamaño total de la ventana (debe ser impar para simetría).
     * @return Lista con los valores suavizados, de la misma longitud que [values].
     */
    private fun lwmaPass(values: List<Float>, windowSize: Int): List<Float> {
        val half = windowSize / 2
        val weights = triangularWeights(half)

        return values.mapIndexed { i, _ ->
            val start = maxOf(0, i - half)
            val end   = minOf(values.lastIndex, i + half)

            var weightedSum = 0f
            var totalWeight = 0f

            for (j in start..end) {
                val kernelIndex = j - i + half
                val w = if (kernelIndex in weights.indices) weights[kernelIndex] else 1f
                weightedSum += values[j] * w
                totalWeight += w
            }

            if (totalWeight > 0f) weightedSum / totalWeight else values[i]
        }
    }

    /**
     * Genera un array de pesos triangulares simétricos de tamaño `2 × half + 1`.
     *
     * Ejemplo con `half = 2`: `[1, 2, 3, 2, 1]`. La normalización se realiza
     * implícitamente al dividir por `totalWeight` en [lwmaPass].
     *
     * @param half Mitad del tamaño de la ventana, sin contar el punto central.
     * @return Array de pesos triangulares.
     */
    private fun triangularWeights(half: Int): FloatArray {
        val size = 2 * half + 1
        return FloatArray(size) { i ->
            (half + 1 - kotlin.math.abs(i - half)).toFloat()
        }
    }

    /**
     * Variante adaptativa que ajusta el tamaño de la ventana según el intervalo
     * de tiempo promedio entre puntos, útil cuando el muestreo es irregular.
     *
     * Calcula el `dt` promedio de la serie y lo usa para convertir la ventana
     * temporal deseada [targetWindowSec] en un número de puntos equivalente.
     *
     * @param points Lista de puntos a suavizar.
     * @param targetWindowSec Duración deseada de la ventana de suavizado en segundos.
     * @param passes Número de pasadas LWMA a aplicar.
     * @return Lista de [CleanPoint] con las coordenadas suavizadas.
     */
    fun smoothAdaptive(
        points: List<CleanPoint>,
        targetWindowSec: Float = 0.1f,
        passes: Int = 2
    ): List<CleanPoint> {
        if (points.size < 3) return points

        val avgDt = if (points.size > 1) {
            (points.last().tSec - points.first().tSec) / (points.size - 1)
        } else {
            0.033f
        }

        val windowSize = maxOf(3, ((targetWindowSec / avgDt).toInt() / 2) * 2 + 1)
        val config = PipelineConfig(
            smoothingWindowSize = windowSize,
            smoothingPasses = passes
        )

        return smooth(points, config)
    }
}