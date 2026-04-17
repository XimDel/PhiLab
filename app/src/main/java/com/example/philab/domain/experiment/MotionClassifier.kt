package com.example.philab.domain.experiment

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clasifica el tipo de movimiento de un objeto a partir de los resultados
 * cinemáticos obtenidos en un experimento.
 *
 * El análisis se basa en la variación de la velocidad y la aceleración
 * calculadas a partir de los puntos registrados.
 */
object MotionClassifier {

    /**
     * Determina el tipo de movimiento según los datos proporcionados.
     *
     * El criterio de clasificación es:
     * - Movimiento Rectilíneo Uniforme (MRU): aceleración promedio cercana a cero.
     * - Movimiento Rectilíneo Uniformemente Acelerado (MRUA): aceleración aproximadamente constante.
     * - Movimiento No Uniforme: aceleración variable.
     *
     * @param results Resultados del experimento que contienen los puntos y métricas calculadas.
     * @return Descripción del tipo de movimiento detectado.
     */
    fun classify(results: ExperimentResults): String {
        val points = results.points
        if (points.size < 3) return "Movimiento detectado"

        val velocities = mutableListOf<Float>()
        for (i in 1 until points.size) {
            val dt = points[i].tSeconds - points[i - 1].tSeconds
            if (dt > 0f) {
                velocities.add((points[i].xCm - points[i - 1].xCm) / dt)
            }
        }

        if (velocities.size < 2) return "Movimiento detectado"

        val accels = mutableListOf<Float>()
        for (i in 1 until velocities.size) {
            accels.add(velocities[i] - velocities[i - 1])
        }

        val meanAccel = abs(results.avgAccelCmS2)
        val accelMean = accels.average().toFloat()
        val accelStd  = sqrt(
            accels.map { (it - accelMean) * (it - accelMean) }.average()
        ).toFloat()

        return when {
            meanAccel < 0.5f -> "Movimiento Rectilíneo Uniforme (MRU)"
            accelStd / (meanAccel + 0.001f) < 1.5f -> "Movimiento Rectilíneo Uniformemente Acelerado (MRUA)"
            else -> "Movimiento No Uniforme"
        }
    }
}