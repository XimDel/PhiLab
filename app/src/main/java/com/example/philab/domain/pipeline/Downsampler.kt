package com.example.philab.domain.pipeline

import kotlin.math.abs
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 5: DOWNSAMPLING (SOLO VISUAL)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reduce el número de puntos para graficar sin perder la forma de la señal.
 *
 * ALGORITMO: Largest Triangle Three Buckets (LTTB) — Steinarsson (2013).
 *
 * POR QUÉ LTTB Y NO STEP FIJO:
 *
 *   Step fijo (cada N puntos): puede saltar picos y valles importantes. Si el
 *   paso cae entre dos máximos, ambos desaparecen de la gráfica.
 *
 *   LTTB: divide los datos en N buckets (uno por punto de salida deseado).
 *   En cada bucket elige el punto que forma el triángulo de mayor área con
 *   el punto ya seleccionado y el centroide del siguiente bucket.
 *   Esto maximiza la información visual preservada — los picos extremos
 *   siempre se seleccionan porque forman triángulos grandes.
 *
 *   Resultado: la gráfica con N puntos es visualmente indistinguible de la
 *   gráfica completa. Ideal para señales de posición / velocidad.
 *
 * COMPLEJIDAD: O(n) — lineal en el número de puntos originales.
 */
object Downsampler {

    /**
     * Devuelve hasta maxPoints pares (t, valor) representativos de la señal.
     * Si points.size ≤ maxPoints, devuelve todos sin modificar.
     */
    fun downsample(
        points: List<Pair<Float, Float>>,   // (t, value)
        maxPoints: Int
    ): List<Pair<Float, Float>> {
        val n = points.size
        if (n <= maxPoints || maxPoints < 3) return points

        return lttb(points, maxPoints)
    }

    // ── Implementación de LTTB ────────────────────────────────────────────────

    private fun lttb(
        data: List<Pair<Float, Float>>,
        threshold: Int
    ): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()

        // Siempre incluir el primer y último punto
        result.add(data.first())

        // Tamaño de cada bucket (excluido primero y último)
        val bucketSize = (data.size - 2).toFloat() / (threshold - 2)

        var a = 0  // índice del último punto seleccionado

        for (i in 0 until threshold - 2) {
            // Rango del bucket actual
            val bucketStart = ((i + 1) * bucketSize + 1).toInt()
            val bucketEnd   = minOf(((i + 2) * bucketSize + 1).toInt(), data.size - 1)

            // Centroide del siguiente bucket (para calcular el área del triángulo)
            val nextBucketStart = bucketEnd
            val nextBucketEnd   = minOf(((i + 2) * bucketSize + 1).toInt(), data.size - 1)
            val avgX = data.subList(nextBucketStart, nextBucketEnd + 1)
                .map { it.first }.average().toFloat()
            val avgY = data.subList(nextBucketStart, nextBucketEnd + 1)
                .map { it.second }.average().toFloat()

            // Punto A (ya seleccionado)
            val pointA = data[a]

            // Elegir el punto del bucket actual que maximiza el área del triángulo A-P-C
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
     * Área del triángulo formado por tres puntos (x,y).
     * Fórmula: |( (x_a)(y_b - y_c) + (x_b)(y_c - y_a) + (x_c)(y_a - y_b) ) / 2|
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

    // ── Adaptador para MotionPoint ────────────────────────────────────────────

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

    data class DownsampledMotion(
        val position: List<Pair<Float, Float>>,
        val velocity: List<Pair<Float, Float>>,
        val acceleration: List<Pair<Float, Float>>
    )
}