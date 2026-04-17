package com.example.philab.data.aruco

/**
 * Representa un diccionario de marcadores ArUco del tipo DICT_4X4_50.
 *
 * Contiene los patrones binarios de los marcadores definidos como matrices de 4×4,
 * donde cada celda indica el color del píxel (0 = blanco, 1 = negro).
 *
 * Este diccionario permite recuperar la representación interna de cada marcador
 * a partir de su identificador.
 */
object ArucoDictionary {

    /**
     * Obtiene la matriz de bits de 4×4 correspondiente a un marcador específico.
     *
     * @param id Identificador del marcador (debe estar en el rango 0 a 49).
     * @return Matriz de enteros de tamaño 4×4 en orden fila-columna.
     * @throws IllegalArgumentException si el identificador está fuera del rango permitido.
     */
    fun getBits(id: Int): Array<IntArray> {
        require(id in 0..49) { "Marker ID must be 0–49 for DICT_4X4_50" }
        return DICT_4X4_50[id]
    }

    /**
     * Arreglo que contiene los patrones binarios de todos los marcadores del diccionario.
     *
     * Cada posición del arreglo corresponde a un ID de marcador y almacena una matriz 4×4
     * que representa su patrón interno.
     */
    private val DICT_4X4_50: Array<Array<IntArray>> = arrayOf(
        arrayOf(
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1)
        ),
        arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 1, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 0, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 0, 0, 0)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 1, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 0, 0)
        )
    )
}
