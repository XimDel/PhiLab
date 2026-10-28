package com.example.philab.domain.experiment

import com.example.philab.domain.pipeline.KinematicFit

/**
 * Clasifica el tipo de movimiento de un objeto a partir de los resultados
 * cinemáticos obtenidos en un experimento.
 *
 * La clasificación la hace [KinematicFit] comparando los modelos MRU (lineal),
 * MRUA (cuadrático) y no uniforme (cúbico) ajustados sobre el eje del movimiento.
 * Un término solo cuenta si es estadísticamente significativo y además tiene un
 * efecto físico apreciable, por lo que el resultado no depende de los FPS del equipo.
 */
object MotionClassifier {

    /**
     * Determina el tipo de movimiento según los datos proporcionados.
     *
     * @param results Resultados del experimento que contienen los puntos registrados.
     * @return Descripción del tipo de movimiento detectado.
     */
    fun classify(results: ExperimentResults): String =
        KinematicFit.fit(results.points).motionType.label
}
