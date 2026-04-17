package com.example.philab.domain.pipeline

import kotlin.math.abs

/**
 * Etapa 1 del pipeline cinemático. Detecta outliers en la señal de posición
 * usando dos capas independientes con criterio AND: un punto solo se marca como
 * inválido si ambas capas lo rechazan simultáneamente.
 *
 * El criterio AND reemplaza al OR de versiones anteriores, que era demasiado
 * agresivo con datos de tracking óptico y eliminaba hasta un 25 % de los puntos,
 * forzando interpolaciones lineales que generaban velocidades espurias.
 *
 * Capa 1 — Velocidad instantánea con MAD:
 * Marca puntos cuya velocidad entre fotogramas consecutivos supera un umbral
 * basado en la mediana absoluta de desviación del conjunto de velocidades.
 *
 * Capa 2 — Posición local con MAD:
 * Marca puntos muy alejados de la mediana de posición dentro de una ventana
 * deslizante centrada en cada punto.
 */
object OutlierDetector {

    /**
     * Analiza [points] y devuelve la misma cantidad de [CleanPoint] con el flag
     * `isValid` indicando si cada punto superó ambas capas de detección.
     *
     * Si la cantidad de puntos es menor que [PipelineConfig.minPointsForStats],
     * todos los puntos se marcan como válidos sin análisis estadístico.
     *
     * @param points Lista de puntos crudos a analizar.
     * @param config Parámetros del pipeline que controlan los umbrales MAD y el tamaño de ventana.
     * @return Lista de [CleanPoint] con el mismo orden que [points] y el flag `isValid` asignado.
     */
    fun detect(
        points: List<RawPoint>,
        config: PipelineConfig
    ): List<CleanPoint> {
        if (points.size < config.minPointsForStats) {
            return points.map { it.toClean(isValid = true) }
        }

        val velocityOutliers = detectByInstantaneousVelocity(points, config)
        val positionOutliers = detectByLocalPosition(points, config)

        val combined = velocityOutliers.zip(positionOutliers) { v, p ->
            v.copy(isValid = v.isValid || p.isValid)
        }

        return combined
    }

    /**
     * Capa 1: marca como outlier los puntos cuya velocidad instantánea supera
     * `mediana + [PipelineConfig.velocityMadMultiplier] × MAD` del conjunto completo de velocidades.
     * Marca tanto el punto origen como el destino del salto detectado.
     *
     * @param points Puntos crudos a analizar.
     * @param config Parámetros del pipeline.
     * @return Lista de [CleanPoint] con `isValid = false` en los puntos con velocidad anómala.
     */
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

    /**
     * Capa 2: marca como outlier los puntos cuya posición se aleja de la mediana
     * local más de `[PipelineConfig.madMultiplier] × MAD` dentro de una ventana deslizante.
     *
     * Cuando el MAD local es prácticamente cero (señal constante), se usa un MAD
     * mínimo estimado a partir del rango global de posiciones para evitar
     * falsos positivos.
     *
     * @param points Puntos crudos a analizar.
     * @param config Parámetros del pipeline.
     * @return Lista de [CleanPoint] con `isValid = false` en los puntos con posición anómala.
     */
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

            result.add(points[i].toClean(isValid = deviation <= config.madMultiplier * effectiveMad))
        }

        return result
    }

    /**
     * Calcula la mediana de una lista de [Float].
     *
     * @param values Lista de valores a ordenar.
     * @return Mediana, o `0f` si la lista está vacía.
     */
    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid    = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f
        else sorted[mid]
    }

    /**
     * Calcula la Desviación Absoluta de la Mediana (MAD) escalada con el factor
     * de consistencia `1.4826` para equivalencia con la desviación estándar bajo
     * distribución normal.
     *
     * @param values Lista de valores.
     * @param med Mediana precalculada de [values].
     * @return MAD escalado.
     */
    fun mad(values: List<Float>, med: Float): Float {
        val deviations = values.map { abs(it - med) }
        return 1.4826f * median(deviations)
    }

    /**
     * Estima un MAD mínimo cuando la señal es casi constante, usando el 2 % del
     * rango global de posiciones o `0.5` como cota inferior absoluta.
     *
     * @param points Conjunto completo de puntos crudos.
     * @return MAD mínimo estimado.
     */
    private fun estimateMinimumMad(points: List<RawPoint>): Float {
        val xs    = points.map { it.x }
        val range = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        return maxOf(range * 0.02f, 0.5f)
    }

    private fun RawPoint.toClean(isValid: Boolean) =
        CleanPoint(tSec = tSec, x = x, y = y, isValid = isValid)
}