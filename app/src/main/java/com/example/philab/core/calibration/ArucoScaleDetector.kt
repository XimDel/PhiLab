package com.example.philab.core.calibration

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Point
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.hypot

/**
 * Detecta marcadores ArUco en un bitmap y calcula el factor de escala en cm/px.
 *
 * Utiliza OpenCV para identificar el marcador más grande visible en el fotograma
 * y aplica un filtro de suavizado exponencial al factor de escala calculado,
 * reduciendo el ruido entre fotogramas consecutivos.
 *
 * @property smoothingAlpha Factor de suavizado exponencial entre 0 y 1.
 *   Valores más bajos producen una escala más estable pero con mayor latencia.
 * @property minMarkerSizePx Tamaño mínimo aceptable del marcador en píxeles.
 *   Los marcadores más pequeños se descartan para evitar mediciones imprecisas.
 */
class ArucoScaleDetector(
    private val smoothingAlpha: Float = 0.20f,
    private val minMarkerSizePx: Float = 24f
) {

    private var smoothedCmPerPx: Float? = null
    private val dictionary by lazy { Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50) }
    private val detectorParameters by lazy { DetectorParameters() }
    private val detector by lazy { ArucoDetector(dictionary, detectorParameters) }
    private val rgbaMat by lazy { Mat() }

    /**
     * Analiza un [bitmap] en busca de un marcador ArUco y devuelve el estado de calibración resultante.
     *
     * Convierte el bitmap a Mat, detecta los marcadores presentes, selecciona el más grande
     * y calcula el factor de escala aplicando suavizado exponencial.
     *
     * @param bitmap Fotograma capturado por la cámara a analizar.
     * @param markerSizeCm Tamaño físico real del marcador ArUco en centímetros.
     * @return [CalibrationState.Calibrated] si se detectó y midió un marcador correctamente,
     *   [CalibrationState.Searching] si no se encontró ningún marcador válido,
     *   o [CalibrationState.Error] si ocurrió un fallo durante el procesamiento.
     */
    fun detectScale(
        bitmap: Bitmap,
        markerSizeCm: Float
    ): CalibrationState {
        if (markerSizeCm <= 0f) {
            return CalibrationState.Error("Tamaño de marcador inválido")
        }

        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        var ids: MatOfInt? = null

        return try {
            ids = MatOfInt()
            val corners = ArrayList<Mat>()

            Utils.bitmapToMat(bitmap, rgbaMat)
            detector.detectMarkers(rgbaMat, corners, ids)

            if (corners.isEmpty() || ids.empty()) {
                corners.forEach { it.release() }
                return CalibrationState.Searching
            }

            val bestIndex = findLargestMarkerIndex(corners)
            if (bestIndex == -1) {
                corners.forEach { it.release() }
                return CalibrationState.Searching
            }

            val markerCornerMat = corners[bestIndex]
            val markerCorners = extractCornerOffsets(markerCornerMat)
            val markerSizePx = estimateMarkerSidePx(markerCornerMat)

            if (!markerSizePx.isFinite() || markerSizePx < minMarkerSizePx) {
                corners.forEach { it.release() }
                return CalibrationState.Searching
            }

            val rawCmPerPx = markerSizeCm / markerSizePx
            val filteredCmPerPx = smooth(rawCmPerPx)
            val markerId = ids.toArray().getOrNull(bestIndex)

            corners.forEach { it.release() }

            CalibrationState.Calibrated(
                cmPerPx = filteredCmPerPx,
                markerSizeCm = markerSizeCm,
                markerSizePx = markerSizePx,
                markerId = markerId,
                corners = markerCorners,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight
            )
        } catch (e: Throwable) {
            CalibrationState.Error("OpenCV/Aruco: ${e.javaClass.simpleName}")
        } finally {
            ids?.release()
        }
    }

    /**
     * Reinicia el valor suavizado del factor de escala.
     *
     * Debe llamarse cuando se interrumpe la calibración o se cambia de marcador,
     * para evitar que valores anteriores influyan en la próxima sesión.
     */
    fun reset() {
        smoothedCmPerPx = null
    }

    /**
     * Libera los recursos nativos de OpenCV asociados al Mat interno.
     *
     * Debe invocarse cuando el detector ya no sea necesario para evitar fugas de memoria nativa.
     */
    fun release() {
        if (rgbaMat.nativeObjAddr != 0L) rgbaMat.release()
    }

    /**
     * Aplica un filtro de suavizado exponencial al [value] recibido.
     *
     * @param value Nuevo valor crudo de cm/px a incorporar.
     * @return Valor suavizado resultante.
     */
    private fun smooth(value: Float): Float {
        val previous = smoothedCmPerPx
        val smoothed = if (previous == null) value
        else previous + smoothingAlpha * (value - previous)
        smoothedCmPerPx = smoothed
        return smoothed
    }

    /**
     * Encuentra el índice del marcador con mayor tamaño estimado en píxeles dentro de [corners].
     *
     * @param corners Lista de matrices de esquinas detectadas por el detector ArUco.
     * @return Índice del marcador más grande, o `-1` si la lista está vacía.
     */
    private fun findLargestMarkerIndex(corners: List<Mat>): Int {
        var bestIndex = -1
        var bestSize = 0f
        corners.forEachIndexed { index, mat ->
            val sizePx = estimateMarkerSidePx(mat)
            if (sizePx > bestSize) {
                bestSize = sizePx
                bestIndex = index
            }
        }
        return bestIndex
    }

    /**
     * Estima el tamaño lateral promedio de un marcador a partir de su matriz de esquinas [cornerMat].
     *
     * Calcula las cuatro distancias entre esquinas consecutivas y devuelve su promedio,
     * ofreciendo una estimación robusta ante leves perspectivas o distorsiones.
     *
     * @param cornerMat Matriz OpenCV con las coordenadas de las cuatro esquinas del marcador.
     * @return Tamaño estimado en píxeles, o `0f` si no se pudieron extraer las esquinas.
     */
    private fun estimateMarkerSidePx(cornerMat: Mat): Float {
        val points = extractCornerPoints(cornerMat)
        if (points.size < 4) return 0f
        val p0 = points[0]; val p1 = points[1]
        val p2 = points[2]; val p3 = points[3]
        val d01 = distance(p0, p1); val d12 = distance(p1, p2)
        val d23 = distance(p2, p3); val d30 = distance(p3, p0)
        return ((d01 + d12 + d23 + d30) / 4.0).toFloat()
    }

    /**
     * Extrae una lista de [Point] desde una [cornerMat] en formato OpenCV.
     *
     * Compatible con matrices de forma `1×N` (una fila, N columnas) y `N×1` (N filas, una columna).
     *
     * @param cornerMat Matriz OpenCV con las coordenadas de las esquinas del marcador.
     * @return Lista de hasta cuatro puntos extraídos, o lista vacía si el formato no es compatible.
     */
    private fun extractCornerPoints(cornerMat: Mat): List<Point> {
        val result = mutableListOf<Point>()
        val rows = cornerMat.rows()
        val cols = cornerMat.cols()
        if (rows == 1 && cols >= 4) {
            for (i in 0 until 4) {
                val xy = cornerMat.get(0, i) ?: continue
                if (xy.size >= 2) result.add(Point(xy[0], xy[1]))
            }
            return result
        }
        if (cols == 1 && rows >= 4) {
            for (i in 0 until 4) {
                val xy = cornerMat.get(i, 0) ?: continue
                if (xy.size >= 2) result.add(Point(xy[0], xy[1]))
            }
            return result
        }
        return result
    }

    /**
     * Convierte las esquinas de una [cornerMat] en una lista de [Offset] de Compose.
     *
     * @param cornerMat Matriz OpenCV con las coordenadas de las esquinas del marcador.
     * @return Lista de [Offset] con las coordenadas en el espacio del bitmap.
     */
    private fun extractCornerOffsets(cornerMat: Mat): List<Offset> {
        return extractCornerPoints(cornerMat).map { p ->
            Offset(p.x.toFloat(), p.y.toFloat())
        }
    }

    /**
     * Calcula la distancia euclidiana entre los puntos [a] y [b].
     *
     * @param a Primer punto.
     * @param b Segundo punto.
     * @return Distancia en píxeles entre los dos puntos.
     */
    private fun distance(a: Point, b: Point): Double {
        return hypot(b.x - a.x, b.y - a.y)
    }
}