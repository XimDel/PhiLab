package com.example.philab.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla principal: una fila por sesión de experimento.
 * Mapea 1:1 con ExperimentResults (sin los puntos, que van en PointEntity).
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val idSession: Long = 0,

    val experimentName: String,
    val selectedLabel: String,
    val recordedAt: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val sampleRateHz: Float,
    val unit: String,
    val cmPerPx: Float,

    // Cinemática
    val totalDistanceCm: Float,
    val displacementCm: Float,
    val avgSpeedCmS: Float,
    val avgAccelCmS2: Float,

    // Rango para graficar
    val minX: Float,
    val maxX: Float,
    val minT: Float,
    val maxT: Float
)