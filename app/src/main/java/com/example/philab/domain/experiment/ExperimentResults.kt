package com.example.philab.domain.experiment

/**
 * Representa los resultados cinemáticos obtenidos al finalizar una sesión de experimento.
 *
 * Contiene tanto los datos originales registrados como los valores derivados del análisis
 * (distancia, velocidad, aceleración), junto con metadatos de la captura.
 *
 * Todas las magnitudes están expresadas en la misma unidad base indicada en [unit].
 */
data class ExperimentResults(

    /**
     * Lista de puntos registrados durante el experimento.
     */
    val points: List<DataPoint>,

    /**
     * Unidad de medida utilizada en los datos.
     * Puede ser "cm" si existe calibración o "px" en caso contrario.
     */
    val unit: String,

    /**
     * Etiqueta seleccionada asociada al objeto o medición.
     */
    val selectedLabel: String,

    /**
     * Duración total de la sesión en milisegundos.
     */
    val durationMs: Long,

    /**
     * Número total de muestras registradas.
     */
    val sampleCount: Int,

    /**
     * Frecuencia real de muestreo en Hertz (Hz).
     */
    val sampleRateHz: Float,

    /**
     * Factor de conversión de píxeles a centímetros.
     * Será 1.0 si no se realizó calibración.
     */
    val cmPerPx: Float,

    /**
     * Marca de tiempo en la que se registraron los resultados.
     */
    val recordedAt: Long = System.currentTimeMillis(),

    /**
     * Distancia total recorrida acumulada (suma de |Δx|).
     */
    val totalDistanceCm: Float,

    /**
     * Desplazamiento neto: diferencia entre posición final e inicial.
     */
    val displacementCm: Float,

    /**
     * Velocidad promedio escalar (distancia total / tiempo).
     */
    val avgSpeedCmS: Float,

    /**
     * Aceleración promedio calculada entre el primer y último intervalo.
     */
    val avgAccelCmS2: Float,

    /**
     * Valor mínimo de posición en el eje X para graficación.
     */
    val minX: Float,

    /**
     * Valor máximo de posición en el eje X para graficación.
     */
    val maxX: Float,

    /**
     * Tiempo mínimo registrado para graficación.
     */
    val minT: Float,

    /**
     * Tiempo máximo registrado para graficación.
     */
    val maxT: Float
) {

    /**
     * Indica si los datos fueron calibrados a unidades físicas (centímetros).
     */
    val isCalibrated: Boolean
        get() = unit == "cm"

    /**
     * Indica si no hay puntos registrados en el experimento.
     */
    val isEmpty: Boolean
        get() = points.isEmpty()
}