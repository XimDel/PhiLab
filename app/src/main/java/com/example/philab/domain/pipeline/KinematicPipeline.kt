package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.ExperimentResults
import kotlin.math.abs

/**
 * Orquestador del pipeline cinemático completo. Transforma un [ExperimentResults]
 * en un [PipelineResult] listo para ser consumido por la UI.
 *
 * Las etapas se ejecutan en orden secuencial:
 *
 * 1. **[RawPointConverter]** — convierte y normaliza los puntos del dominio.
 * 2. **[OutlierDetector]** — marca outliers sin eliminarlos todavía.
 * 3. **[Smoother]** — suaviza posición sobre todos los puntos, incluyendo los marcados como inválidos,
 *    para reducir el impacto de los saltos en los bordes.
 * 4. Filtrado — descarta los puntos inválidos sin interpolar gaps.
 * 5. **[DerivativeCalculator]** — calcula velocidad y aceleración con diferencias centrales.
 * 6. Clip de percentil 98 — limita valores extremos de velocidad y aceleración.
 * 7. Suavizado de velocidad y aceleración con más pasadas que la posición.
 * 8. **[Downsampler]** — reduce los puntos para graficar usando LTTB.
 */
object KinematicPipeline {

    /**
     * Ejecuta el pipeline completo sobre los resultados de un experimento.
     *
     * Si la señal tiene menos de 3 puntos tras la conversión o tras el filtrado
     * de outliers, devuelve un [PipelineResult] vacío con derivadas en cero.
     *
     * @param results Resultado de la sesión de experimento a procesar.
     * @param config Parámetros del pipeline. Si se omite se usan los valores por defecto.
     * @return [PipelineResult] con los puntos crudos, limpios, de movimiento y datos de gráfica.
     */
    fun process(
        results: ExperimentResults,
        config: PipelineConfig = PipelineConfig()
    ): PipelineResult {

        val raw = RawPointConverter.fromExperimentResults(results)

        if (raw.size < 3) return emptyResult(raw, results.unit)

        val afterOutlierDetection = OutlierDetector.detect(raw, config)
        val outliersRemoved = afterOutlierDetection.count { !it.isValid }

        val smoothedAll = Smoother.smooth(afterOutlierDetection, config)

        val validOnly = smoothedAll.filter { it.isValid }

        if (validOnly.size < 3) return emptyResult(raw, results.unit)

        val motionPoints = DerivativeCalculator.calculate(validOnly, config)

        val clipped = clipExtremes(motionPoints)

        val motionSmoothed = smoothMotion(clipped, config)

        val downsampled = Downsampler.downsampleMotion(motionSmoothed, config.maxChartPoints)

        val chart = ChartData(
            position         = downsampled.position,
            velocity         = downsampled.velocity,
            acceleration     = downsampled.acceleration,
            unit             = results.unit,
            totalPoints      = raw.size,
            cleanedPoints    = validOnly.size,
            outliersRemoved  = outliersRemoved,
            gapsInterpolated = 0
        )

        return PipelineResult(
            raw    = raw,
            clean  = validOnly,
            motion = motionSmoothed,
            chart  = chart
        )
    }

    /**
     * Clampea los valores de velocidad y aceleración al percentil 98 de sus
     * respectivos valores absolutos, evitando que un spike aislado distorsione
     * la escala de las gráficas.
     *
     * No tiene efecto si [points] tiene menos de 5 elementos.
     *
     * @param points Lista de [MotionPoint] a procesar.
     * @return Nueva lista con velocidad y aceleración limitadas al rango `[-p98, p98]`.
     */
    private fun clipExtremes(points: List<MotionPoint>): List<MotionPoint> {
        if (points.size < 5) return points

        val speeds = points.map { kotlin.math.abs(it.velocity) }.sorted()
        val accels = points.map { kotlin.math.abs(it.acceleration) }.sorted()

        val p98index = (speeds.size * 0.98f).toInt().coerceAtMost(speeds.lastIndex)
        val maxSpeed = speeds[p98index]
        val maxAccel = accels[p98index]

        return points.map { mp ->
            mp.copy(
                velocity     = mp.velocity.coerceIn(-maxSpeed, maxSpeed),
                acceleration = mp.acceleration.coerceIn(-maxAccel, maxAccel)
            )
        }
    }

    /**
     * Aplica suavizado adicional sobre las series de velocidad y aceleración
     * con más pasadas que las usadas para la posición, dado que son señales
     * de mayor orden y más susceptibles al ruido.
     *
     * @param points Lista de [MotionPoint] con velocidad y aceleración calculadas.
     * @param config Parámetros del pipeline con el número de pasadas base y el tamaño de ventana.
     * @return Lista de [MotionPoint] con velocidad y aceleración suavizadas.
     */
    private fun smoothMotion(
        points: List<MotionPoint>,
        config: PipelineConfig
    ): List<MotionPoint> {
        if (points.size < 3) return points

        var velocities    = points.map { it.velocity }
        var accelerations = points.map { it.acceleration }

        val velPasses   = config.smoothingPasses + 1
        val accelPasses = config.smoothingPasses + 2
        val accelWindow = config.smoothingWindowSize + 4

        repeat(velPasses) {
            velocities = smoothFloatList(velocities, config.smoothingWindowSize)
        }
        repeat(accelPasses) {
            accelerations = smoothFloatList(accelerations, accelWindow)
        }

        return points.mapIndexed { i, mp ->
            mp.copy(velocity = velocities[i], acceleration = accelerations[i])
        }
    }

    /**
     * Aplica una media móvil simple de ventana [windowSize] sobre una lista de flotantes.
     *
     * @param values Lista de valores a suavizar.
     * @param windowSize Número de puntos de la ventana deslizante.
     * @return Lista suavizada de la misma longitud que [values].
     */
    private fun smoothFloatList(values: List<Float>, windowSize: Int): List<Float> {
        val half = windowSize / 2
        return values.mapIndexed { i, _ ->
            val start = maxOf(0, i - half)
            val end   = minOf(values.lastIndex, i + half)
            values.subList(start, end + 1).average().toFloat()
        }
    }

    /**
     * Construye un [PipelineResult] vacío con derivadas en cero para cuando
     * la señal tiene muy pocos puntos para ser procesada.
     *
     * @param raw Lista de puntos crudos originales.
     * @param unit Unidad de medida del experimento.
     * @return [PipelineResult] con velocidad y aceleración en cero en todos los puntos.
     */
    private fun emptyResult(raw: List<RawPoint>, unit: String): PipelineResult {
        val clean  = raw.map { CleanPoint(it.tSec, it.x, it.y, isValid = true) }
        val motion = raw.map { MotionPoint(it.tSec, it.x, it.y, 0f, 0f) }
        return PipelineResult(
            raw   = raw, clean = clean, motion = motion,
            chart = ChartData(
                position         = raw.map { Pair(it.tSec, it.x) },
                velocity         = raw.map { Pair(it.tSec, 0f) },
                acceleration     = raw.map { Pair(it.tSec, 0f) },
                unit             = unit,
                totalPoints      = raw.size,
                cleanedPoints    = raw.size,
                outliersRemoved  = 0,
                gapsInterpolated = 0
            )
        )
    }
}