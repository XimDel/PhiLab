package com.example.philab.domain.pipeline

import kotlin.math.abs

/**
 * Etapa de derivación del pipeline cinemático. Estima posición, velocidad (`dx/dt`) y
 * aceleración (`d²x/dt²`) con regresión polinomial local (Savitzky–Golay para
 * muestreo irregular).
 *
 * Para cada punto se ajusta una parábola por mínimos cuadrados a los vecinos dentro de
 * una ventana de tiempo y se evalúan su valor y sus derivadas en `t_i`. A diferencia de
 * derivar dos veces con diferencias finitas y luego suavizar:
 * - no introduce retraso (la ventana está centrada en `t_i`),
 * - es exacto para un MRUA (una parábola no se distorsiona),
 * - funciona con timestamps irregulares, como los que produce la inferencia asíncrona.
 *
 * La aceleración usa una ventana más ancha que la velocidad porque la segunda derivada
 * amplifica mucho más el ruido. En los extremos la ventana se desplaza hacia el interior
 * en lugar de recortarse, para conservar el número de puntos del ajuste.
 */
object DerivativeCalculator {

    /**
     * Calcula posición suavizada, velocidad y aceleración para cada punto de [points].
     *
     * Si [points] tiene menos de 3 elementos, se devuelven con velocidad y aceleración cero.
     *
     * @param points Lista de puntos limpios ordenados por tiempo ascendente.
     * @param config Parámetros del pipeline: [PipelineConfig.velocityWindowSec],
     *   [PipelineConfig.accelerationWindowSec] y [PipelineConfig.minWindowPoints].
     * @return Lista de [MotionPoint] con la misma longitud que [points].
     */
    fun calculate(
        points: List<CleanPoint>,
        config: PipelineConfig
    ): List<MotionPoint> {
        if (points.size < 3) {
            return points.map { it.toMotion(x = it.x, velocity = 0f, acceleration = 0f) }
        }

        val t = DoubleArray(points.size) { points[it].tSec.toDouble() }
        val x = DoubleArray(points.size) { points[it].x.toDouble() }
        val minPoints = config.minWindowPoints.coerceAtLeast(3)

        return points.mapIndexed { i, cp ->
            val velFit = localFit(t, x, i, config.velocityWindowSec / 2.0, minPoints)
            val accFit = localFit(t, x, i, config.accelerationWindowSec / 2.0, minPoints + 2)

            val position = velFit?.valueAt(t[i]) ?: x[i]
            val velocity = velFit?.let { it.coefficients[1] / it.scale } ?: 0.0
            val acceleration = accFit?.let { 2.0 * it.coefficients[2] / (it.scale * it.scale) } ?: 0.0

            cp.toMotion(
                x            = position.toFloat(),
                velocity     = velocity.toFloat(),
                acceleration = acceleration.toFloat()
            )
        }
    }

    /**
     * Ajusta una parábola centrada en `t[i]` a los puntos con `|t - t[i]| <= halfWindowSec`.
     * Si son menos de [minPoints], amplía la ventana con los vecinos más cercanos en tiempo.
     */
    private fun localFit(
        t: DoubleArray,
        x: DoubleArray,
        i: Int,
        halfWindowSec: Double,
        minPoints: Int
    ): LeastSquares.PolyFit? {
        val n = t.size
        var lo = i
        var hi = i
        while (lo > 0 && t[i] - t[lo - 1] <= halfWindowSec) lo--
        while (hi < n - 1 && t[hi + 1] - t[i] <= halfWindowSec) hi++
        val target = minOf(minPoints, n)
        while (hi - lo + 1 < target) {
            val canLo = lo > 0
            val canHi = hi < n - 1
            when {
                canLo && canHi -> if (t[i] - t[lo - 1] <= t[hi + 1] - t[i]) lo-- else hi++
                canLo -> lo--
                canHi -> hi++
                else -> break
            }
        }
        val indices = IntArray(hi - lo + 1) { lo + it }
        return LeastSquares.fit(t, x, degree = 2, indices = indices, center = t[i])
    }

    /**
     * Estadísticas cinemáticas agregadas calculadas sobre una lista de [MotionPoint].
     *
     * @property maxSpeed Velocidad máxima en valor absoluto.
     * @property avgSpeed Velocidad media en valor absoluto.
     * @property maxAcceleration Aceleración máxima en valor absoluto.
     * @property avgAcceleration Aceleración media en valor absoluto.
     * @property rmsVelocity Velocidad cuadrática media (RMS).
     */
    data class KinematicStats(
        val maxSpeed: Float,
        val avgSpeed: Float,
        val maxAcceleration: Float,
        val avgAcceleration: Float,
        val rmsVelocity: Float
    )

    /**
     * Calcula las estadísticas cinemáticas agregadas de una lista de [MotionPoint].
     *
     * @param motionPoints Lista de puntos de movimiento procesados.
     * @return [KinematicStats] con los valores calculados, o todos ceros si la lista está vacía.
     */
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

    private fun CleanPoint.toMotion(x: Float, velocity: Float, acceleration: Float) = MotionPoint(
        tSec         = tSec,
        x            = x,
        y            = y,
        velocity     = velocity,
        acceleration = acceleration
    )
}