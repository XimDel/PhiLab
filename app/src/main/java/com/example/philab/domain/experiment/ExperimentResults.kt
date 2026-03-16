package com.example.philab.domain.experiment

/**
 * Resultados cinemáticos calculados al finalizar la sesión.
 * Todos los valores usan la misma unidad que los DataPoints (cm o px).
 */
data class ExperimentResults(
    val points: List<DataPoint>,
    val unit: String,               // "cm" o "px"
    val selectedLabel: String,

    // Metadatos de la sesión
    val durationMs: Long,
    val sampleCount: Int,
    val sampleRateHz: Float,        // frecuencia real de muestreo
    val cmPerPx: Float,             // 1.0 si no había calibración
    val recordedAt: Long = System.currentTimeMillis(),

    // Cinemática
    val totalDistanceCm: Float,     // distancia total recorrida (|Δx| acumulado)
    val displacementCm: Float,      // desplazamiento neto x(final) - x(inicio)
    val avgSpeedCmS: Float,         // distancia / tiempo escalar
    // avgVelocityCmS eliminado — redundante con avgSpeedCmS para el caso 1D actual
    val avgAccelCmS2: Float,        // aceleración media entre primer y último intervalo

    // Para graficar x(t)
    val minX: Float,
    val maxX: Float,
    val minT: Float,
    val maxT: Float
) {
    val isCalibrated: Boolean get() = unit == "cm"
    val isEmpty: Boolean get() = points.isEmpty()
}