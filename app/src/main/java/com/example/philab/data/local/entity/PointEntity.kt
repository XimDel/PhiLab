package com.example.philab.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad que representa un punto capturado dentro de una sesión.
 *
 * Cada registro almacena la posición del objeto en un instante de tiempo,
 * formando parte de una serie temporal asociada a una sesión específica.
 *
 * Está relacionada con [SessionEntity] mediante una clave foránea,
 * eliminándose en cascada cuando la sesión es borrada.
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

    /**
     * Identificador único del punto.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Identificador de la sesión a la que pertenece el punto.
     */
    val idSession: Long,

    /**
     * Índice del punto dentro de la secuencia temporal.
     */
    val index: Int,

    /**
     * Tiempo en segundos desde el inicio de la captura.
     */
    val tSeconds: Float,

    /**
     * Posición en el eje X en centímetros.
     */
    val xCm: Float,

    /**
     * Posición en el eje Y en centímetros.
     */
    val yCm: Float
)