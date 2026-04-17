package com.example.philab.domain.pipeline

import kotlin.math.abs

/**
 * Etapa 5 del pipeline cinemático. Reduce el número de puntos para graficar
 * preservando la forma visual de la señal mediante el algoritmo
 * Largest Triangle Three Buckets (LTTB) de Steinarsson (2013).
 *
 * A diferencia del paso fijo, LTTB divide los datos en N buckets y selecciona
 * en cada uno el punto que maximiza el área del triángulo formado con el último
 * punto seleccionado y el centroide del siguiente bucket. Esto garantiza que
 * los picos y valles importantes siempre queden representados en la salida.
 *
 * Complejidad: `O(n)` lineal en el número de puntos originales.
 */
object Downsampler {

    /**
     * Reduce [points] a un máximo de [maxPoints] pares `(t, valor)` representativos.
     *
     * Si `points.size ≤ maxPoints` o `maxPoints < 3`, devuelve la lista original sin modificar.
     *
     * @param points Lista de pares `(tiempo, valor)` de la señal original.
     * @param maxPoints Número máximo de puntos en la salida.
     * @return Lista reducida manteniendo la forma visual de la señal original.
     */
    fun downsample(
        points: List<Pair<Float, Float>>,
        maxPoints: Int
    ): List<Pair<Float, Float>> {
        val n = points.size
        if (n <= maxPoints || maxPoints < 3) return points

        return lttb(points, maxPoints)
    }

    /**
     * Implementación del algoritmo LTTB.
     *
     * Siempre incluye el primer y el último punto. Para cada uno de los
     * `threshold - 2` buckets intermedios selecciona el punto que forma el
     * triángulo de mayor área con el punto ya elegido y el centroide del
     * bucket siguiente.
     *
     * @param data Lista completa de pares `(t, valor)`.
     * @param threshold Número de puntos deseado en la salida, incluyendo extremos.
     * @return Lista de [threshold] puntos seleccionados.
     */
    private fun lttb(
        data: List<Pair<Float, Float>>,
        threshold: Int
    ): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()

        result.add(data.first())

        val bucketSize = (data.size - 2).toFloat() / (threshold - 2)

        var a = 0

        for (i in 0 until threshold - 2) {
            val bucketStart = ((i + 1) * bucketSize + 1).toInt()
            val bucketEnd   = minOf(((i + 2) * bucketSize + 1).toInt(), data.size - 1)

            val nextBucketStart = bucketEnd
            val nextBucketEnd   = minOf(((i + 2) * bucketSize + 1).toInt(), data.size - 1)
            val avgX = data.subList(nextBucketStart, nextBucketEnd + 1)
                .map { it.first }.average().toFloat()
            val avgY = data.subList(nextBucketStart, nextBucketEnd + 1)
                .map { it.second }.average().toFloat()

            val pointA = data[a]

            var maxArea = -1f
            var maxIndex = bucketStart

            for (j in bucketStart until minOf(bucketEnd + 1, data.size - 1)) {
                val p = data[j]
                val area = triangleArea(pointA, p, Pair(avgX, avgY))
                if (area > maxArea) {
                    maxArea = area
                    maxIndex = j
                }
            }

            result.add(data[maxIndex])
            a = maxIndex
        }

        result.add(data.last())
        return result
    }

    /**
     * Calcula el área del triángulo formado por tres puntos `(x, y)` usando la
     * fórmula de la cruz: `|(xa(yb − yc) + xb(yc − ya) + xc(ya − yb)) / 2|`.
     *
     * @param a Primer vértice.
     * @param b Segundo vértice.
     * @param c Tercer vértice.
     * @return Área del triángulo en unidades cuadradas del espacio `(t, valor)`.
     */
    private fun triangleArea(
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        c: Pair<Float, Float>
    ): Float {
        return abs(
            (a.first  * (b.second - c.second) +
                    b.first  * (c.second - a.second) +
                    c.first  * (a.second - b.second)) / 2f
        )
    }

    /**
     * Aplica [downsample] de forma independiente sobre las series de posición,
     * velocidad y aceleración de una lista de [MotionPoint].
     *
     * @param points Lista de puntos de movimiento procesados.
     * @param maxPoints Número máximo de puntos por serie en la salida.
     * @return [DownsampledMotion] con las tres series reducidas.
     */
    fun downsampleMotion(
        points: List<MotionPoint>,
        maxPoints: Int
    ): DownsampledMotion {
        val positionPairs  = points.map { Pair(it.tSec, it.x) }
        val velocityPairs  = points.map { Pair(it.tSec, it.velocity) }
        val accelPairs     = points.map { Pair(it.tSec, it.acceleration) }

        return DownsampledMotion(
            position     = downsample(positionPairs, maxPoints),
            velocity     = downsample(velocityPairs, maxPoints),
            acceleration = downsample(accelPairs, maxPoints)
        )
    }

    /**
     * Resultado del downsampling aplicado a las tres series cinemáticas de un experimento.
     *
     * @property position Serie de pares `(tiempo, posición)` reducida.
     * @property velocity Serie de pares `(tiempo, velocidad)` reducida.
     * @property acceleration Serie de pares `(tiempo, aceleración)` reducida.
     */
    data class DownsampledMotion(
        val position: List<Pair<Float, Float>>,
        val velocity: List<Pair<Float, Float>>,
        val acceleration: List<Pair<Float, Float>>
    )
}