package com.example.philab.domain.model

/**
 * Modelo de datos que representa un artículo teórico.
 *
 * Contiene la información necesaria para mostrar un artículo en la aplicación,
 * incluyendo su identificador, título, imagen asociada y contenido.
 *
 * @param id Identificador único del artículo.
 * @param title Título del artículo.
 * @param image Nombre del recurso drawable asociado como imagen de portada.
 * @param content Contenido completo del artículo en formato texto.
 */
data class Article(
    val id: String,
    val title: String,
    val image: String,
    val content: String
)