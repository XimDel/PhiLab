package com.example.philab.domain.model

/**
 * Modelo de datos que representa una pregunta frecuente (FAQ).
 *
 * Contiene la información necesaria para mostrar una pregunta, su respuesta
 * y opcionalmente una acción asociada como mostrar una imagen o navegar
 * a otra pantalla.
 *
 * @param question Texto de la pregunta.
 * @param answer Texto de la respuesta.
 * @param actionType Tipo de acción asociada (por ejemplo: "image", "navigate").
 * @param actionPayload Información adicional requerida para ejecutar la acción,
 * como el nombre de un recurso o una ruta de navegación.
 */
data class Faq(
    val question: String,
    val answer: String,
    val actionType: String? = null,
    val actionPayload: String? = null
)