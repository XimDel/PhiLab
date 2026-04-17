package com.example.philab.domain.pipeline

import kotlin.math.abs

/**
 * Etapa 4 del pipeline cinemático. Calcula velocidad (`dx/dt`) y aceleración (`dv/dt`)
 * usando diferencias centrales de segundo orden sobre los [CleanPoint] válidos.
 *
 * Las diferencias centrales tienen error `O(dt²)` y son simétricas en fase,
 * lo que significa que el valor calculado corresponde exactamente al instante `t_i`
 * sin sesgo temporal. En los extremos de la señal se usan diferencias unilaterales
 * de primer orden para no extrapolar fuera del rango de datos.
 *
 * El intervalo de tiempo `dt` se clampea a [PipelineConfig.minDtMs] para evitar
 * divisiones por cero cuando hay timestamps casi idénticos por jitter del sistema.
 */
object DerivativeCalculator {

    /**
     * Calcula velocidad y aceleración para cada punto de [points] y devuelve
     * la lista equivalente de [MotionPoint].
     *
     * Si [points] tiene un solo elemento, se devuelve con velocidad y aceleración cero.
     *
     * @param points Lista de puntos limpios y suavizados, ordenados por tiempo ascendente.
     * @param config Parámetros del pipeline, en particular [PipelineConfig.minDtMs].
     * @return Lista de [MotionPoint] con la misma longitud que [points].
     */
    fun calculate(
        points: List<CleanPoint>,
        config: PipelineConfig
    ): List<MotionPoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) {
            return listOf(points[0].toMotion(velocity = 0f, acceleration = 0f))
        }

        val velocities = computeVelocities(points, config.minDtMs)
        val accelerations = computeAccelerations(velocities, points, config.minDtMs)

        return points.mapIndexed { i, cp ->
            cp.toMotion(
                velocity     = velocities[i],
                acceleration = accelerations[i]
            )
        }
    }

    /**
     * Calcula las velocidades instantáneas usando diferencias centrales en el interior
     * y diferencias unilaterales en los extremos.
     *
     * @param points Lista de puntos limpios ordenados por tiempo.
     * @param minDt Intervalo de tiempo mínimo en segundos para evitar divisiones por cero.
     * @return Array de velocidades de la misma longitud que [points].
     */
    private fun computeVelocities(
        points: List<CleanPoint>,
        minDt: Float
    ): FloatArray {
        val n = points.size
        val v = FloatArray(n)

        for (i in points.indices) {
            v[i] = when (i) {
                0 -> {
                    val dt = (points[1].tSec - points[0].tSec).coerceAtLeast(minDt)
                    (points[1].x - points[0].x) / dt
                }
                n - 1 -> {
                    val dt = (points[n - 1].tSec - points[n - 2].tSec).coerceAtLeast(minDt)
                    (points[n - 1].x - points[n - 2].x) / dt
                }
                else -> {
                    val dt = (points[i + 1].tSec - points[i - 1].tSec).coerceAtLeast(minDt * 2)
                    (points[i + 1].x - points[i - 1].x) / dt
                }
            }
        }

        return v
    }

    /**
     * Calcula las aceleraciones aplicando el mismo esquema de diferencias centrales
     * sobre el array de velocidades ya calculado.
     *
     * @param velocities Array de velocidades calculado por [computeVelocities].
     * @param points Lista de puntos original, usada para obtener los timestamps.
     * @param minDt Intervalo de tiempo mínimo en segundos.
     * @return Array de aceleraciones de la misma longitud que [velocities].
     */
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

    private fun CleanPoint.toMotion(velocity: Float, acceleration: Float) = MotionPoint(
        tSec         = tSec,
        x            = x,
        y            = y,
        velocity     = velocity,
        acceleration = acceleration
    )
}