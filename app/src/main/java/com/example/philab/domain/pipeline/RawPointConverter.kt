package com.example.philab.domain.pipeline

import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults

/**
 * Etapa 0 del pipeline cinemático. Convierte [DataPoint] del dominio en [RawPoint]
 * normalizados y validados para las etapas siguientes.
 *
 * Responsabilidades:
 * - Ordenar los puntos por timestamp ascendente.
 * - Eliminar duplicados con el mismo `tMs` (mismo fotograma procesado dos veces).
 * - Descartar puntos con valores `NaN` o infinitos producidos por bugs del tracker.
 * - Normalizar el tiempo para que el primer punto sea `t = 0.0 s`.
 */
object RawPointConverter {

    /**
     * Convierte una lista de [DataPoint] en [RawPoint] listos para el pipeline.
     *
     * @param points Lista de puntos del dominio, potencialmente desordenados o con duplicados.
     * @return Lista de [RawPoint] ordenada, sin duplicados, sin valores no finitos
     *   y con tiempo normalizado a cero en el primer punto. Devuelve lista vacía
     *   si [points] está vacía.
     */
    fun fromDataPoints(points: List<DataPoint>): List<RawPoint> {
        if (points.isEmpty()) return emptyList()

        return points
            .sortedBy { it.tMs }
            .distinctBy { it.tMs }
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

    /**
     * Convierte los puntos contenidos en un [ExperimentResults] en [RawPoint]
     * aplicando las mismas transformaciones que [fromDataPoints].
     *
     * @param results Resultado de una sesión de experimento.
     * @return Lista de [RawPoint] procesados.
     */
    fun fromExperimentResults(results: ExperimentResults): List<RawPoint> =
        fromDataPoints(results.points)
}