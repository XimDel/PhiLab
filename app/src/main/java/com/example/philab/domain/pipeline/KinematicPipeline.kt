package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.ExperimentResults

/**
 * Orquestador del pipeline cinemático completo. Transforma un [ExperimentResults]
 * en un [PipelineResult] listo para ser consumido por la UI.
 *
 * Las etapas se ejecutan en orden secuencial:
 *
 * 1. **[RawPointConverter]** — convierte y normaliza los puntos del dominio.
 * 2. **Proyección** — con el eje obtenido por [KinematicFit], cada punto pasa a ser la
 *    posición sobre la línea del movimiento, medida desde el punto de partida. Así las
 *    gráficas son correctas para movimiento horizontal, vertical o inclinado.
 * 3. **[OutlierDetector]** — marca outliers sobre la posición proyectada.
 * 4. Filtrado — descarta los puntos inválidos sin interpolar gaps.
 * 5. **[DerivativeCalculator]** — posición suavizada, velocidad y aceleración por
 *    regresión polinomial local, sin retraso ni recortes de valores extremos.
 * 6. **[Downsampler]** — reduce los puntos para graficar usando LTTB.
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
     *   En los puntos limpios y de movimiento, `x` es la posición sobre el eje del movimiento
     *   e `y` la desviación perpendicular a él.
     */
    fun process(
        results: ExperimentResults,
        config: PipelineConfig = PipelineConfig()
    ): PipelineResult {

        val raw = RawPointConverter.fromExperimentResults(results)

        if (raw.size < 3) return emptyResult(raw, results.unit)

        val projected = projectOntoMotionAxis(raw)

        val afterOutlierDetection = OutlierDetector.detect(projected, config)
        val outliersRemoved = afterOutlierDetection.count { !it.isValid }

        val validOnly = afterOutlierDetection.filter { it.isValid }

        if (validOnly.size < 3) return emptyResult(raw, results.unit)

        val motionPoints = DerivativeCalculator.calculate(validOnly, config)

        val downsampled = Downsampler.downsampleMotion(motionPoints, config.maxChartPoints)

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
            motion = motionPoints,
            chart  = chart
        )
    }

    /**
     * Convierte cada punto (x, y) en (posición sobre el eje, desviación perpendicular).
     *
     * La posición se mide desde el inicio del movimiento según el ajuste, de modo que la
     * gráfica de posición arranca en ~0 y crece en el sentido del desplazamiento.
     */
    private fun projectOntoMotionAxis(raw: List<RawPoint>): List<RawPoint> {
        val fit = KinematicFit.fitRaw(raw)
        val start = fit.positionAt(raw.first().tSec) ?: fit.project(raw.first().x, raw.first().y)
        return raw.map { p ->
            RawPoint(
                tSec = p.tSec,
                x = fit.project(p.x, p.y) - start,
                y = fit.perpendicular(p.x, p.y)
            )
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
