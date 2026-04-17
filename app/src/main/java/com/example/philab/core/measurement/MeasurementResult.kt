package com.example.philab.core.measurement

/**
 * Resultado de la medición física de un objeto detectado.
 *
 * Contiene las dimensiones del bounding box expresadas en píxeles,
 * centímetros y metros, junto con el centroide del objeto en el espacio
 * de imagen.
 *
 * @property label     Etiqueta de clase asignada por el detector al objeto medido.
 * @property widthPx   Ancho del bounding box en píxeles.
 * @property heightPx  Alto del bounding box en píxeles.
 * @property widthCm   Ancho convertido a centímetros mediante la calibración ArUco.
 * @property heightCm  Alto convertido a centímetros mediante la calibración ArUco.
 * @property widthM    Ancho en metros (`widthCm / 100`).
 * @property heightM   Alto en metros (`heightCm / 100`).
 * @property centerX   Coordenada X del centroide del bounding box en píxeles.
 * @property centerY   Coordenada Y del centroide del bounding box en píxeles.
 */
data class MeasurementResult(
    val label: String,
    val widthPx: Float,
    val heightPx: Float,
    val widthCm: Float,
    val heightCm: Float,
    val widthM: Float,
    val heightM: Float,
    val centerX: Float,
    val centerY: Float
)