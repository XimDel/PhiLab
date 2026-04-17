package com.example.philab.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa una sesión de experimento.
 *
 * Contiene la información general y métricas calculadas del experimento,
 * excluyendo los puntos individuales que se almacenan en [PointEntity].
 */
@Entity(tableName = "sessions")
data class SessionEntity(

    /**
     * Identificador único de la sesión.
     */
    @PrimaryKey(autoGenerate = true)
    val idSession: Long = 0,

    /**
     * Nombre del experimento.
     */
    val experimentName: String,

    /**
     * Etiqueta seleccionada para el objeto rastreado.
     */
    val selectedLabel: String,

    /**
     * Marca de tiempo en milisegundos cuando se registró la sesión.
     */
    val recordedAt: Long,

    /**
     * Duración total de la sesión en milisegundos.
     */
    val durationMs: Long,

    /**
     * Número total de muestras registradas.
     */
    val sampleCount: Int,

    /**
     * Frecuencia de muestreo en Hertz.
     */
    val sampleRateHz: Float,

    /**
     * Unidad de medida utilizada en el experimento.
     */
    val unit: String,

    /**
     * Factor de conversión de píxeles a centímetros.
     */
    val cmPerPx: Float,

    /**
     * Distancia total recorrida en centímetros.
     */
    val totalDistanceCm: Float,

    /**
     * Desplazamiento neto en centímetros.
     */
    val displacementCm: Float,

    /**
     * Velocidad promedio en centímetros por segundo.
     */
    val avgSpeedCmS: Float,

    /**
     * Aceleración promedio en centímetros por segundo al cuadrado.
     */
    val avgAccelCmS2: Float,

    /**
     * Valor mínimo de la posición en el eje X.
     */
    val minX: Float,

    /**
     * Valor máximo de la posición en el eje X.
     */
    val maxX: Float,

    /**
     * Tiempo mínimo registrado en segundos.
     */
    val minT: Float,

    /**
     * Tiempo máximo registrado en segundos.
     */
    val maxT: Float
)