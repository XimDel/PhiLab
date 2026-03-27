package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 0: CONVERSIÓN Y VALIDACIÓN INICIAL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convierte los DataPoint del dominio existente en RawPoints para el pipeline.
 *
 * Responsabilidades:
 *  - Ordenar por tiempo (los timestamps del SessionRecorder son monótonos, pero
 *    si hay concurrencia podrían llegar desordenados).
 *  - Eliminar duplicados de timestamp exacto (mismo tMs → mismo frame).
 *  - Rechazar puntos con valores NaN / Infinite (defensa ante bugs de tracking).
 *  - Normalizar el tiempo a t=0.0 al primer punto.
 */
object RawPointConverter {

    fun fromDataPoints(points: List<DataPoint>): List<RawPoint> {
        if (points.isEmpty()) return emptyList()

        return points
            .sortedBy { it.tMs }
            .distinctBy { it.tMs }                       // eliminar duplicados exactos
            .filter { it.xCm.isFinite() && it.yCm.isFinite() }
            .let { sorted ->
                val t0 = sorted.first().tMs
                sorted.map { dp ->
                    RawPoint(
                        tSec = (dp.tMs - t0) / 1000f,
                        x = dp.xCm,
                        y = dp.yCm
                    )
                }
            }
    }

    fun fromExperimentResults(results: ExperimentResults): List<RawPoint> =
        fromDataPoints(results.points)
}