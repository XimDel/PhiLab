package com.example.philab.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de puntos capturados
 * Cada fila pertenece a una sesión (FK → sessions.idSession).
 * ON DELETE CASCADE: al borrar una sesión se borran sus puntos automáticamente.
 */
@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["idSession"],
            childColumns = ["idSession"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idSession")]
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val idSession: Long,
    val index: Int,
    val tSeconds: Float,
    val xCm: Float,
    val yCm: Float
)