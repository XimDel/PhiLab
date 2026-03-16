package com.example.philab.domain.experiment

/**
 * Un punto de datos capturado durante el experimento.
 *
 * @param tMs     Tiempo en milisegundos desde el inicio de la sesión
 * @param xCm     Posición horizontal del centroide en la unidad activa (cm o px)
 * @param yCm     Posición vertical del centroide en la unidad activa (cm o px)
 */
data class DataPoint(
    val tMs: Long,
    val xCm: Float,
    val yCm: Float
) {
    val tSeconds: Float get() = tMs / 1000f
}