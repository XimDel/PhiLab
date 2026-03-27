package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.ExperimentResults
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// ORQUESTADOR: KinematicPipeline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Secuencia de etapas:
 *
 *   ExperimentResults
 *     │
 *     ▼  [0] RawPointConverter
 *   List<RawPoint>
 *     │
 *     ▼  [1] OutlierDetector  (solo marca, NO elimina)
 *   List<CleanPoint>
 *     │
 *     ▼  [2] Smoother sobre posición COMPLETA (incluyendo inválidos pesados)
 *   List<CleanPoint>  (x e y suavizados)
 *     │
 *     ▼  [3] Filtrar solo puntos válidos (sin gaps, sin interpolación)
 *   List<CleanPoint>
 *     │
 *     ▼  [4] DerivativeCalculator  (diferencias centrales)
 *   List<MotionPoint>
 *     │
 *     ▼  [5] Clip de velocidad/aceleración extremos (percentil 98)
 *   List<MotionPoint>
 *     │
 *     ▼  [6] Suavizado de velocidad y aceleración
 *   List<MotionPoint>
 *     │
 *     ▼  [7] Downsampler (LTTB)
 *   ChartData
 *
 * CAMBIOS RESPECTO A LA VERSIÓN ANTERIOR:
 *
 *  1. El GapHandler fue ELIMINADO del pipeline principal.
 *     Los gaps interpolados generaban bordes con velocidades espurias enormes
 *     (salto de posición lineal entre dos puntos alejados → v = Δx/Δt enorme).
 *     La solución correcta es simplemente no interpolar: filtrar los outliers
 *     y calcular derivadas solo sobre los puntos válidos consecutivos.
 *
 *  2. El OutlierDetector ahora usa criterio AND (ambas capas deben rechazar).
 *     Antes usaba OR → demasiado agresivo con datos de tracking de cámara.
 *
 *  3. El suavizado se aplica ANTES de filtrar outliers usando peso reducido
 *     para los puntos inválidos. Esto suaviza la transición en los bordes
 *     sin inventar puntos nuevos.
 *
 *  4. Se agrega clip de percentil para velocidad y aceleración: los valores
 *     extremos (>percentil 98) se clamean al máximo razonable, evitando que
 *     un solo spike arruine la escala de la gráfica.
 *
 *  5. El smoother de velocidad/aceleración usa más pasadas (3 vs 2).
 */
object KinematicPipeline {

    fun process(
        results: ExperimentResults,
        config: PipelineConfig = PipelineConfig()
    ): PipelineResult {

        // ── [0] Conversión ───────────────────────────────────────────────────
        val raw = RawPointConverter.fromExperimentResults(results)

        if (raw.size < 3) return emptyResult(raw, results.unit)

        // ── [1] Detección de outliers (marca, no elimina) ────────────────────
        val afterOutlierDetection = OutlierDetector.detect(raw, config)
        val outliersRemoved = afterOutlierDetection.count { !it.isValid }

        // ── [2] Suavizado sobre todos los puntos (incluyendo inválidos) ──────
        // Suavizamos primero para que los puntos inválidos reciban influencia
        // de sus vecinos válidos, reduciendo el impacto de los saltos.
        val smoothedAll = Smoother.smooth(afterOutlierDetection, config)

        // ── [3] Filtrar: solo puntos válidos para las derivadas ──────────────
        // SIN interpolación de gaps. Simplemente descartamos los inválidos.
        // Las derivadas en los bordes de cada gap usarán diferencias unilaterales
        // (DerivativeCalculator ya maneja esto correctamente en los extremos).
        val validOnly = smoothedAll.filter { it.isValid }

        if (validOnly.size < 3) return emptyResult(raw, results.unit)

        // ── [4] Derivadas ─────────────────────────────────────────────────────
        val motionPoints = DerivativeCalculator.calculate(validOnly, config)

        // ── [5] Clip de valores extremos (percentil 98) ───────────────────────
        // Evita que un spike aislado arruine la escala de la gráfica.
        val clipped = clipExtremes(motionPoints)

        // ── [6] Suavizado de velocidad y aceleración ──────────────────────────
        val motionSmoothed = smoothMotion(clipped, config)

        // ── [7] Downsampling y construcción de ChartData ──────────────────────
        val downsampled = Downsampler.downsampleMotion(motionSmoothed, config.maxChartPoints)

        val chart = ChartData(
            position         = downsampled.position,
            velocity         = downsampled.velocity,
            acceleration     = downsampled.acceleration,
            unit             = results.unit,
            totalPoints      = raw.size,
            cleanedPoints    = validOnly.size,
            outliersRemoved  = outliersRemoved,
            gapsInterpolated = 0   // ya no interpolamos
        )

        return PipelineResult(
            raw    = raw,
            clean  = validOnly,
            motion = motionSmoothed,
            chart  = chart
        )
    }

    // ── Clip de percentil 98 sobre velocidad y aceleración ────────────────────

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

    // ── Suavizado de velocidad y aceleración ──────────────────────────────────

    private fun smoothMotion(
        points: List<MotionPoint>,
        config: PipelineConfig
    ): List<MotionPoint> {
        if (points.size < 3) return points

        var velocities    = points.map { it.velocity }
        var accelerations = points.map { it.acceleration }

        // Más pasadas sobre velocidad (señal de primer orden, aún ruidosa)
        val velPasses   = config.smoothingPasses + 1
        // Más pasadas sobre aceleración (segunda derivada, muy ruidosa)
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

    private fun smoothFloatList(values: List<Float>, windowSize: Int): List<Float> {
        val half = windowSize / 2
        return values.mapIndexed { i, _ ->
            val start = maxOf(0, i - half)
            val end   = minOf(values.lastIndex, i + half)
            values.subList(start, end + 1).average().toFloat()
        }
    }

    // ── Resultado vacío ───────────────────────────────────────────────────────

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