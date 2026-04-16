package com.example.philab.core.camera

/**
 * Representación de una detección de objeto lista para ser consumida por la capa de UI.
 *
 * Encapsula la etiqueta, la confianza y el bounding box normalizado o en coordenadas
 * del bitmap fuente, junto con las dimensiones de dicho bitmap para permitir el
 * escalado al espacio de pantalla en los composables.
 *
 * @property label Etiqueta de la clase detectada.
 * @property score Confianza de la detección, en el rango `[0.0, 1.0]`.
 * @property left Coordenada izquierda del bounding box en píxeles del bitmap fuente.
 * @property top Coordenada superior del bounding box en píxeles del bitmap fuente.
 * @property right Coordenada derecha del bounding box en píxeles del bitmap fuente.
 * @property bottom Coordenada inferior del bounding box en píxeles del bitmap fuente.
 * @property sourceWidth Ancho del bitmap fuente en píxeles.
 * @property sourceHeight Alto del bitmap fuente en píxeles.
 * @property isSelected Indica si esta detección está actualmente seleccionada por el usuario.
 */
data class UiDetection(
    val label: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val isSelected: Boolean = false,
)