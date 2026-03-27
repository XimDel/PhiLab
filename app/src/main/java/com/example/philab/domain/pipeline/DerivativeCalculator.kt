package com.example.philab.domain.pipeline

import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 4: DERIVADAS (VELOCIDAD Y ACELERACIÓN)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calcula velocidad (dx/dt) y aceleración (dv/dt) usando diferencias centrales.
 *
 * POR QUÉ DIFERENCIAS CENTRALES Y NO HACIA ADELANTE/ATRÁS:
 *
 * • Diferencia hacia adelante:  v_i = (x_{i+1} - x_i) / dt
 *   → Error O(dt). El resultado tiene sesgo temporal (el valor está medio dt
 *     adelantado). Peor en presencia de ruido.
 *
 * • Diferencia central:  v_i = (x_{i+1} - x_{i-1}) / (2·dt)
 *   → Error O(dt²). Simétrico → sin sesgo de fase. Más robusto ante ruido.
 *   → La velocidad calculada corresponde exactamente al instante t_i.
 *
 * ESTABILIDAD NUMÉRICA:
 *   - dt se clampea a minDt para evitar divisiones por cero o inestabilidad
 *     cuando hay timestamps casi idénticos (jitter del sistema).
 *   - Los extremos (i=0, i=n-1) usan diferencias unilaterales de primer orden
 *     en lugar de inventar puntos fuera del rango.
 *   - La aceleración hereda la suavidad de la velocidad ya calculada.
 *
 * SEGUNDA DERIVADA (ACELERACIÓN):
 *   a_i = (v_{i+1} - v_{i-1}) / (2·dt_avg)
 *   Se aplica el mismo esquema central. Si la velocidad ya está suavizada
 *   por el Smoother, la aceleración resultante será razonablemente estable.
 *
 * NOTA: Para datos muy ruidosos después de un experimento corto, la aceleración
 *   puede seguir siendo ruidosa incluso tras el suavizado. En ese caso, el
 *   Smoother puede aplicarse también sobre los MotionPoints resultantes
 *   (ver KinematicPipeline.kt).
 */
object DerivativeCalculator {

    fun calculate(
        points: List<CleanPoint>,
        config: PipelineConfig
    ): List<MotionPoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) {
            return listOf(points[0].toMotion(velocity = 0f, acceleration = 0f))
        }

        // Paso 1: calcular velocidades con diferencias centrales
        val velocities = computeVelocities(points, config.minDtMs)

        // Paso 2: calcular aceleraciones sobre las velocidades
        val accelerations = computeAccelerations(velocities, points, config.minDtMs)

        return points.mapIndexed { i, cp ->
            cp.toMotion(
                velocity     = velocities[i],
                acceleration = accelerations[i]
            )
        }
    }

    // ── Velocidades: diferencias centrales ───────────────────────────────────

    private fun computeVelocities(
        points: List<CleanPoint>,
        minDt: Float
    ): FloatArray {
        val n = points.size
        val v = FloatArray(n)

        for (i in points.indices) {
            v[i] = when (i) {
                // Extremo izquierdo: diferencia hacia adelante
                0 -> {
                    val dt = (points[1].tSec - points[0].tSec).coerceAtLeast(minDt)
                    (points[1].x - points[0].x) / dt
                }
                // Extremo derecho: diferencia hacia atrás
                n - 1 -> {
                    val dt = (points[n - 1].tSec - points[n - 2].tSec).coerceAtLeast(minDt)
                    (points[n - 1].x - points[n - 2].x) / dt
                }
                // Interior: diferencia central de orden 2
                else -> {
                    val dt = (points[i + 1].tSec - points[i - 1].tSec).coerceAtLeast(minDt * 2)
                    (points[i + 1].x - points[i - 1].x) / dt
                }
            }
        }

        return v
    }

    // ── Aceleraciones: diferencias centrales sobre velocidades ───────────────

    private fun computeAccelerations(
        velocities: FloatArray,
        points: List<CleanPoint>,
        minDt: Float
    ): FloatArray {
        val n = velocities.size
        val a = FloatArray(n)

        for (i in velocities.indices) {
            a[i] = when (i) {
                0 -> {
                    val dt = (points[1].tSec - points[0].tSec).coerceAtLeast(minDt)
                    (velocities[1] - velocities[0]) / dt
                }
                n - 1 -> {
                    val dt = (points[n - 1].tSec - points[n - 2].tSec).coerceAtLeast(minDt)
                    (velocities[n - 1] - velocities[n - 2]) / dt
                }
                else -> {
                    val dt = (points[i + 1].tSec - points[i - 1].tSec).coerceAtLeast(minDt * 2)
                    (velocities[i + 1] - velocities[i - 1]) / dt
                }
            }
        }

        return a
    }

    // ── Estadísticos para la UI ───────────────────────────────────────────────

    data class KinematicStats(
        val maxSpeed: Float,
        val avgSpeed: Float,
        val maxAcceleration: Float,
        val avgAcceleration: Float,
        val rmsVelocity: Float
    )

    fun computeStats(motionPoints: List<MotionPoint>): KinematicStats {
        if (motionPoints.isEmpty()) return KinematicStats(0f, 0f, 0f, 0f, 0f)

        val speeds = motionPoints.map { abs(it.velocity) }
        val accels = motionPoints.map { abs(it.acceleration) }
        val avgSpeed = speeds.average().toFloat()
        val rms = kotlin.math.sqrt(motionPoints.map { it.velocity * it.velocity }
            .average()).toFloat()

        return KinematicStats(
            maxSpeed          = speeds.maxOrNull() ?: 0f,
            avgSpeed          = avgSpeed,
            maxAcceleration   = accels.maxOrNull() ?: 0f,
            avgAcceleration   = accels.average().toFloat(),
            rmsVelocity       = rms
        )
    }

    private fun CleanPoint.toMotion(velocity: Float, acceleration: Float) = MotionPoint(
        tSec         = tSec,
        x            = x,
        y            = y,
        velocity     = velocity,
        acceleration = acceleration
    )
}