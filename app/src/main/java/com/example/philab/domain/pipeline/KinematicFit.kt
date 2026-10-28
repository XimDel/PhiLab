package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.DataPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estima los parámetros cinemáticos de un movimiento rectilíneo ajustando por
 * mínimos cuadrados los modelos físicos directamente sobre la trayectoria medida,
 * en lugar de derivar numéricamente la posición (lo que amplifica el ruido).
 *
 * Procedimiento:
 * 1. **Eje del movimiento**: se obtiene por análisis de componentes principales de
 *    los puntos (x, y). Así el resultado es válido para movimiento horizontal,
 *    vertical (caída libre) o inclinado. El eje se orienta en el sentido del
 *    desplazamiento neto, de modo que una aceleración positiva significa que el
 *    objeto acelera y una negativa que frena.
 * 2. **Proyección**: cada punto se convierte en una posición escalar `s(t)` sobre el eje.
 * 3. **Ajuste robusto**: se ajusta `s(t) = s₀ + v₀·t + ½·a·t²` y se descartan
 *    iterativamente los puntos con residuo mayor a [OUTLIER_SIGMAS] desviaciones robustas.
 * 4. **Modelos**: se ajustan también el modelo lineal (MRU) y el cúbico, y se elige
 *    el tipo de movimiento según la significancia estadística y la relevancia física
 *    de cada término.
 *
 * Las incertidumbres reportadas son el error estándar de cada parámetro (1σ) a partir
 * de la dispersión de los residuos. No incluyen el error de calibración de la escala.
 */
object KinematicFit {

    /** Número de desviaciones robustas a partir del cual un residuo se considera atípico. */
    private const val OUTLIER_SIGMAS = 4.0

    /** Valor del estadístico t a partir del cual un término se considera significativo (~99.7 %). */
    private const val SIGNIFICANCE_T = 3.0

    /**
     * Cambio mínimo de velocidad durante el experimento, relativo a la velocidad media,
     * para considerar la aceleración físicamente relevante y no un MRU.
     */
    private const val MIN_RELATIVE_VELOCITY_CHANGE = 0.10

    /**
     * Desviación mínima de la velocidad respecto al modelo MRUA, relativa a la escala de
     * velocidades del experimento, para clasificar el movimiento como no uniforme.
     */
    private const val MIN_RELATIVE_JERK_EFFECT = 0.25

    /** Mínimo de puntos para intentar el ajuste. */
    private const val MIN_POINTS = 4

    /** Tipo de movimiento detectado y su descripción para la UI. */
    enum class MotionType(val label: String) {
        INSUFFICIENT_DATA("Movimiento detectado"),
        STATIONARY("Sin movimiento significativo"),
        UNIFORM("Movimiento Rectilíneo Uniforme (MRU)"),
        UNIFORMLY_ACCELERATED("Movimiento Rectilíneo Uniformemente Acelerado (MRUA)"),
        NON_UNIFORM("Movimiento No Uniforme")
    }

    /**
     * Resultado del ajuste. Todas las magnitudes están en la unidad del experimento
     * (cm o px) y segundos.
     *
     * @property axisX Componente X del vector unitario del eje del movimiento.
     * @property axisY Componente Y del vector unitario del eje del movimiento.
     * @property originX Coordenada X del origen usado para proyectar sobre el eje.
     * @property originY Coordenada Y del origen usado para proyectar sobre el eje.
     * @property startTimeSec Tiempo del primer punto usado.
     * @property endTimeSec Tiempo del último punto usado.
     * @property meanVelocity Velocidad media sobre el eje (desplazamiento / tiempo).
     * @property meanVelocityStdErr Error estándar de [meanVelocity].
     * @property initialVelocity Velocidad al inicio del registro según el modelo elegido.
     * @property initialVelocityStdErr Error estándar de [initialVelocity].
     * @property acceleration Aceleración del ajuste cuadrático (positiva = acelera, negativa = frena).
     * @property accelerationStdErr Error estándar de [acceleration].
     * @property displacement Desplazamiento neto sobre el eje según el modelo elegido.
     * @property displacementStdErr Error estándar de [displacement].
     * @property distance Distancia recorrida (considera cambios de sentido).
     * @property residualStd Desviación estándar del ruido de posición (residuos del modelo elegido).
     * @property rSquared Coeficiente de determinación del modelo elegido.
     * @property usedPoints Puntos usados en el ajuste final, tras descartar atípicos.
     * @property totalPoints Puntos recibidos.
     * @property motionType Clasificación del movimiento.
     */
    data class Result(
        val axisX: Float,
        val axisY: Float,
        val originX: Float,
        val originY: Float,
        val startTimeSec: Float,
        val endTimeSec: Float,
        val meanVelocity: Float,
        val meanVelocityStdErr: Float,
        val initialVelocity: Float,
        val initialVelocityStdErr: Float,
        val acceleration: Float,
        val accelerationStdErr: Float,
        val displacement: Float,
        val displacementStdErr: Float,
        val distance: Float,
        val residualStd: Float,
        val rSquared: Float,
        val usedPoints: Int,
        val totalPoints: Int,
        val motionType: MotionType,
        private val model: LeastSquares.PolyFit? = null
    ) {
        /** Proyecta un punto (x, y) sobre el eje del movimiento. */
        fun project(x: Float, y: Float): Float = (x - originX) * axisX + (y - originY) * axisY

        /** Distancia perpendicular con signo de un punto (x, y) al eje del movimiento. */
        fun perpendicular(x: Float, y: Float): Float = -(x - originX) * axisY + (y - originY) * axisX

        /** Posición sobre el eje según el modelo elegido, o `null` si no hubo ajuste. */
        fun positionAt(tSec: Float): Float? = model?.valueAt(tSec.toDouble())?.toFloat()
    }

    /** Ajusta los puntos crudos de un experimento. */
    fun fit(points: List<DataPoint>): Result = fitRaw(RawPointConverter.fromDataPoints(points))

    /**
     * Ajusta una lista de puntos con tiempo relativo en segundos.
     *
     * @param points Puntos ordenados por tiempo, sin tiempos repetidos.
     */
    fun fitRaw(points: List<RawPoint>): Result {
        val n = points.size
        if (n < MIN_POINTS) return insufficient(points)

        val t = DoubleArray(n) { points[it].tSec.toDouble() }
        val x = DoubleArray(n) { points[it].x.toDouble() }
        val y = DoubleArray(n) { points[it].y.toDouble() }

        var inliers = IntArray(n) { it }
        var axis = principalAxis(x, y, inliers)
        var s = project(x, y, axis, inliers)

        for (iteration in 0 until 3) {
            val trial = LeastSquares.fit(t, s, 2, inliers) ?: return insufficient(points)
            val next = rejectOutliers(t, s, x, y, axis, trial, inliers)
            if (next.size < MIN_POINTS || next.contentEquals(inliers)) break
            inliers = next
            axis = principalAxis(x, y, inliers)
            s = project(x, y, axis, inliers)
        }

        val q = LeastSquares.fit(t, s, 2, inliers) ?: return insufficient(points)
        val lin = LeastSquares.fit(t, s, 1, inliers) ?: return insufficient(points)
        val cubic = if (inliers.size >= 8) LeastSquares.fit(t, s, 3, inliers) else null

        val tStart = inliers.minOf { t[it] }
        val tEnd = inliers.maxOf { t[it] }
        val duration = tEnd - tStart

        // Velocidad media del modelo cuadrático en [tStart, tEnd]:
        // (s(tEnd) - s(tStart)) / T = (c1 + c2·(τa + τb)) / h
        val tauA = (tStart - q.center) / q.scale
        val tauB = (tEnd - q.center) / q.scale
        val gMeanQ = doubleArrayOf(0.0, 1.0 / q.scale, (tauA + tauB) / q.scale)
        val meanVQ = dot(gMeanQ, q.coefficients)
        val meanVQErr = sqrt(q.varianceOf(gMeanQ))

        val gAccel = doubleArrayOf(0.0, 0.0, 2.0 / (q.scale * q.scale))
        val accel = dot(gAccel, q.coefficients)
        val accelErr = sqrt(q.varianceOf(gAccel))

        val gV0Q = doubleArrayOf(0.0, 1.0 / q.scale, 2.0 * tauA / q.scale)
        val v0Q = dot(gV0Q, q.coefficients)
        val v0QErr = sqrt(q.varianceOf(gV0Q))

        val gVLin = doubleArrayOf(0.0, 1.0 / lin.scale)
        val vLin = dot(gVLin, lin.coefficients)
        val vLinErr = sqrt(lin.varianceOf(gVLin))

        val type = classify(
            vLin = vLin, vLinErr = vLinErr,
            meanV = meanVQ, accel = accel, accelErr = accelErr,
            duration = duration, cubic = cubic, inlierCount = inliers.size
        )

        val useLinear = type == MotionType.UNIFORM || type == MotionType.STATIONARY
        val chosen = if (useLinear) lin else q
        val meanV = if (useLinear) vLin else meanVQ
        val meanVErr = if (useLinear) vLinErr else meanVQErr

        val distance = when (type) {
            MotionType.NON_UNIFORM -> hysteresisDistance(s, inliers, OUTLIER_SIGMAS / 2 * sqrt(q.residualVariance))
            MotionType.UNIFORMLY_ACCELERATED -> quadraticDistance(v0Q, accel, duration)
            else -> abs(meanV * duration)
        }

        val meanS = inliers.sumOf { s[it] } / inliers.size
        val tss = inliers.sumOf { (s[it] - meanS) * (s[it] - meanS) }
        val rSquared = if (tss > 0) 1.0 - chosen.rss / tss else 0.0

        return Result(
            axisX = axis.first.toFloat(),
            axisY = axis.second.toFloat(),
            originX = axisOriginX(x, inliers).toFloat(),
            originY = axisOriginY(y, inliers).toFloat(),
            startTimeSec = tStart.toFloat(),
            endTimeSec = tEnd.toFloat(),
            meanVelocity = meanV.toFloat(),
            meanVelocityStdErr = meanVErr.toFloat(),
            initialVelocity = (if (useLinear) vLin else v0Q).toFloat(),
            initialVelocityStdErr = (if (useLinear) vLinErr else v0QErr).toFloat(),
            acceleration = accel.toFloat(),
            accelerationStdErr = accelErr.toFloat(),
            displacement = (meanV * duration).toFloat(),
            displacementStdErr = (meanVErr * duration).toFloat(),
            distance = distance.toFloat(),
            residualStd = sqrt(chosen.residualVariance).toFloat(),
            rSquared = rSquared.toFloat(),
            usedPoints = inliers.size,
            totalPoints = n,
            motionType = type,
            model = chosen
        )
    }

    /**
     * Decide el tipo de movimiento.
     *
     * - STATIONARY: la velocidad media no es distinguible de cero.
     * - UNIFORM: la aceleración no es significativa, o lo es pero cambia la velocidad
     *   menos de [MIN_RELATIVE_VELOCITY_CHANGE] durante el registro.
     * - NON_UNIFORM: el término cúbico (sobreacelaración) es significativo y modifica
     *   la velocidad más de [MIN_RELATIVE_JERK_EFFECT] de su escala.
     * - UNIFORMLY_ACCELERATED: en otro caso.
     */
    private fun classify(
        vLin: Double, vLinErr: Double,
        meanV: Double, accel: Double, accelErr: Double,
        duration: Double, cubic: LeastSquares.PolyFit?, inlierCount: Int
    ): MotionType {
        if (inlierCount < MIN_POINTS || duration <= 0.0) return MotionType.INSUFFICIENT_DATA
        if (abs(vLin) <= SIGNIFICANCE_T * vLinErr && abs(accel) <= SIGNIFICANCE_T * accelErr) {
            return MotionType.STATIONARY
        }

        val velocityScale = maxOf(abs(meanV), abs(accel) * duration)

        if (cubic != null) {
            val c3 = cubic.coefficients[3]
            val c3Err = sqrt(cubic.covariance[3][3])
            // Contribución del término cúbico a la velocidad en los extremos: 3·c3·τ²/h con |τ| = 1.
            val jerkEffect = 3.0 * abs(c3) / cubic.scale
            if (abs(c3) > SIGNIFICANCE_T * c3Err && jerkEffect > MIN_RELATIVE_JERK_EFFECT * velocityScale) {
                return MotionType.NON_UNIFORM
            }
        }

        val accelSignificant = inlierCount >= 6 && abs(accel) > SIGNIFICANCE_T * accelErr
        val accelRelevant = abs(accel) * duration > MIN_RELATIVE_VELOCITY_CHANGE * abs(meanV)
        return if (accelSignificant && accelRelevant) MotionType.UNIFORMLY_ACCELERATED
        else MotionType.UNIFORM
    }

    /**
     * Distancia recorrida por un MRUA durante [duration], considerando un posible cambio
     * de sentido cuando la velocidad cruza cero dentro del intervalo.
     */
    private fun quadraticDistance(v0: Double, a: Double, duration: Double): Double {
        val tTurn = if (a != 0.0) -v0 / a else -1.0
        return if (tTurn > 0.0 && tTurn < duration) {
            abs(v0 * tTurn + 0.5 * a * tTurn * tTurn) +
                    abs((v0 + a * tTurn) * (duration - tTurn) + 0.5 * a * (duration - tTurn) * (duration - tTurn))
        } else {
            abs(v0 * duration + 0.5 * a * duration * duration)
        }
    }

    /**
     * Distancia recorrida sobre el eje sumando solo los avances mayores a [threshold].
     *
     * A diferencia de un umbral por segmento, el avance se acumula: un objeto lento
     * suma su distancia aunque cada paso individual sea menor que el ruido.
     */
    private fun hysteresisDistance(s: DoubleArray, inliers: IntArray, threshold: Double): Double {
        if (inliers.isEmpty()) return 0.0
        var anchor = s[inliers[0]]
        var total = 0.0
        for (i in inliers) {
            val step = abs(s[i] - anchor)
            if (step > threshold) { total += step; anchor = s[i] }
        }
        return total + abs(s[inliers.last()] - anchor)
    }

    /**
     * Devuelve los índices cuyos residuos (sobre el eje y perpendicular a él) están
     * dentro de [OUTLIER_SIGMAS] desviaciones robustas (MAD).
     */
    private fun rejectOutliers(
        t: DoubleArray, s: DoubleArray, x: DoubleArray, y: DoubleArray,
        axis: Pair<Double, Double>, fit: LeastSquares.PolyFit, inliers: IntArray
    ): IntArray {
        val along = DoubleArray(s.size) { s[it] - fit.valueAt(t[it]) }
        val perp = DoubleArray(s.size) { -x[it] * axis.second + y[it] * axis.first }

        val alongSigma = robustSigma(inliers.map { along[it] }, s, inliers)
        val perpMedian = median(inliers.map { perp[it] })
        val perpSigma = robustSigma(inliers.map { perp[it] - perpMedian }, s, inliers)

        return s.indices.filter {
            abs(along[it]) <= OUTLIER_SIGMAS * alongSigma &&
                    abs(perp[it] - perpMedian) <= OUTLIER_SIGMAS * perpSigma
        }.toIntArray()
    }

    /** 1.4826·MAD de los residuos, con un mínimo proporcional al rango para datos sin ruido. */
    private fun robustSigma(residuals: List<Double>, s: DoubleArray, inliers: IntArray): Double {
        val mad = 1.4826 * median(residuals.map { abs(it) })
        val range = inliers.maxOf { s[it] } - inliers.minOf { s[it] }
        return maxOf(mad, range * 1e-4, 1e-9)
    }

    /** Vector unitario de la dirección de máxima varianza, orientado según el desplazamiento neto. */
    private fun principalAxis(x: DoubleArray, y: DoubleArray, idx: IntArray): Pair<Double, Double> {
        val mx = axisOriginX(x, idx)
        val my = axisOriginY(y, idx)
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in idx) {
            val dx = x[i] - mx; val dy = y[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        var ux = cos(theta); var uy = sin(theta)

        val q = maxOf(1, idx.size / 4)
        val head = idx.take(q); val tail = idx.takeLast(q)
        val dX = tail.sumOf { x[it] } / q - head.sumOf { x[it] } / q
        val dY = tail.sumOf { y[it] } / q - head.sumOf { y[it] } / q
        if (dX * ux + dY * uy < 0) { ux = -ux; uy = -uy }
        return Pair(ux, uy)
    }

    private fun axisOriginX(x: DoubleArray, idx: IntArray) = idx.sumOf { x[it] } / idx.size
    private fun axisOriginY(y: DoubleArray, idx: IntArray) = idx.sumOf { y[it] } / idx.size

    /** Proyección de todos los puntos sobre el eje, tomando como origen el centroide de [idx]. */
    private fun project(x: DoubleArray, y: DoubleArray, axis: Pair<Double, Double>, idx: IntArray): DoubleArray {
        val ox = axisOriginX(x, idx)
        val oy = axisOriginY(y, idx)
        return DoubleArray(x.size) { (x[it] - ox) * axis.first + (y[it] - oy) * axis.second }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var r = 0.0
        for (i in a.indices) r += a[i] * b[i]
        return r
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun insufficient(points: List<RawPoint>): Result {
        val dx = if (points.size >= 2) points.last().x - points.first().x else 0f
        val dy = if (points.size >= 2) points.last().y - points.first().y else 0f
        val len = sqrt(dx * dx + dy * dy)
        val ux = if (len > 0f) dx / len else 1f
        val uy = if (len > 0f) dy / len else 0f
        val duration = if (points.size >= 2) points.last().tSec - points.first().tSec else 0f
        val meanV = if (duration > 0f) len / duration else 0f
        return Result(
            axisX = ux, axisY = uy,
            originX = points.firstOrNull()?.x ?: 0f, originY = points.firstOrNull()?.y ?: 0f,
            startTimeSec = points.firstOrNull()?.tSec ?: 0f,
            endTimeSec = points.lastOrNull()?.tSec ?: 0f,
            meanVelocity = meanV, meanVelocityStdErr = 0f,
            initialVelocity = meanV, initialVelocityStdErr = 0f,
            acceleration = 0f, accelerationStdErr = 0f,
            displacement = len, displacementStdErr = 0f, distance = len,
            residualStd = 0f, rSquared = 0f,
            usedPoints = points.size, totalPoints = points.size,
            motionType = MotionType.INSUFFICIENT_DATA
        )
    }
}
