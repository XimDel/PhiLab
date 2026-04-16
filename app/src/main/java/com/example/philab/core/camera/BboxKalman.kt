package com.example.philab.core.camera

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.video.KalmanFilter

/**
 * Aplica un filtro de Kalman para suavizar y predecir la posición de un bounding box.
 *
 * Internamente maneja 8 valores: las 4 coordenadas del rectángulo
 * (left, top, right, bottom) y la velocidad de cada una (vl, vt, vr, vb).
 * Se usan las coordenadas, que vienen de las detecciones.
 *
 * Cuando el objeto deja de detectarse (por ejemplo, se oculta), el filtro no se detiene:
 * en vez de actualizar con nuevas mediciones, predice dónde debería estar.
 * Esto permite mantener el seguimiento por varios frames consecutivos
 * (hasta `MAX_FRAMES_LOST`) sin perder completamente el objeto.
 */
class BboxKalman {
    private var kf: KalmanFilter? = null
    private var initialized = false
    private var framesLost = 0

    /** Número máximo de fotogramas consecutivos sin medición antes de reiniciar el filtro. */
    val MAX_FRAMES_LOST = 15

    /**
     * Devuelve la instancia del filtro de Kalman, creándola y configurándola si aún no existe.
     *
     * Configura las matrices de transición, medición, ruido de proceso, ruido de medición
     * y covarianza de error inicial con valores por defecto.
     *
     * @return Instancia de [KalmanFilter] lista para usar.
     */
    private fun getOrCreateKf(): KalmanFilter {
        kf?.let { return it }
        val newKf = KalmanFilter(8, 4, 0, CvType.CV_32F)
        val tm = Mat.eye(8, 8, CvType.CV_32F)
        tm.put(0, 4, floatArrayOf(1f))
        tm.put(1, 5, floatArrayOf(1f))
        tm.put(2, 6, floatArrayOf(1f))
        tm.put(3, 7, floatArrayOf(1f))
        newKf.set_transitionMatrix(tm)
        tm.release()
        val mm = Mat.zeros(4, 8, CvType.CV_32F)
        mm.put(0, 0, floatArrayOf(1f))
        mm.put(1, 1, floatArrayOf(1f))
        mm.put(2, 2, floatArrayOf(1f))
        mm.put(3, 3, floatArrayOf(1f))
        newKf.set_measurementMatrix(mm)
        mm.release()
        val pnc = Mat.eye(8, 8, CvType.CV_32F)
        Core.setIdentity(pnc, Scalar(1e-2))
        newKf.set_processNoiseCov(pnc)
        pnc.release()
        val mnc = Mat.eye(4, 4, CvType.CV_32F)
        Core.setIdentity(mnc, Scalar(5e-2))
        newKf.set_measurementNoiseCov(mnc)
        mnc.release()
        val ecp = Mat.eye(8, 8, CvType.CV_32F)
        Core.setIdentity(ecp, Scalar(1.0))
        newKf.set_errorCovPost(ecp)
        ecp.release()
        kf = newKf
        return newKf
    }

    /**
     * Incorpora una medición válida al filtro y devuelve el bbox corregido.
     *
     * Si el filtro no estaba inicializado, inicializa el estado con las coordenadas
     * del [bbox] recibido y velocidades en cero. Luego ejecuta el ciclo predict-correct.
     *
     * @param bbox Bounding box medido por el detector o tracker en este fotograma.
     * @return Bounding box suavizado tras la corrección del filtro.
     */
    fun update(bbox: Rect): Rect {
        framesLost = 0
        val filter = getOrCreateKf()
        if (!initialized) {
            val state = Mat.zeros(8, 1, CvType.CV_32F)
            state.put(0, 0, floatArrayOf(bbox.left.toFloat()))
            state.put(1, 0, floatArrayOf(bbox.top.toFloat()))
            state.put(2, 0, floatArrayOf(bbox.right.toFloat()))
            state.put(3, 0, floatArrayOf(bbox.bottom.toFloat()))
            filter.set_statePost(state)
            state.release()
            initialized = true
        }
        filter.predict()
        val measurement = Mat.zeros(4, 1, CvType.CV_32F)
        measurement.put(0, 0, floatArrayOf(bbox.left.toFloat()))
        measurement.put(1, 0, floatArrayOf(bbox.top.toFloat()))
        measurement.put(2, 0, floatArrayOf(bbox.right.toFloat()))
        measurement.put(3, 0, floatArrayOf(bbox.bottom.toFloat()))
        val corrected = filter.correct(measurement)
        measurement.release()
        return corrected.toRect()
    }

    /**
     * Predice la posición del objeto durante un fotograma de oclusión sin medición.
     *
     * Incrementa el contador de fotogramas perdidos. Si supera [MAX_FRAMES_LOST],
     * reinicia el filtro y devuelve `null`.
     *
     * @return Bounding box predicho, o `null` si se superó el límite de fotogramas perdidos.
     */
    fun predict(): Rect? {
        if (!initialized) return null
        framesLost++
        if (framesLost > MAX_FRAMES_LOST) {
            reset()
            return null
        }
        return getOrCreateKf().predict().toRect()
    }

    /**
     * Reinicia el filtro descartando el estado acumulado.
     *
     * Tras esta llamada [isInitialized] devuelve `false` y el filtro queda listo
     * para una nueva medición inicial en [update].
     */
    fun reset() {
        initialized = false
        framesLost = 0
        kf = null
    }

    /**
     * Indica si el filtro fue inicializado con al menos una medición válida.
     *
     * @return `true` si se ha llamado a [update] al menos una vez desde el último [reset].
     */
    fun isInitialized() = initialized

    /**
     * Convierte el vector de estado del filtro en un [Rect] de Android.
     *
     * Garantiza que las coordenadas sean no negativas y que `right > left + 4`
     * y `bottom > top + 4` para evitar bounding boxes degenerados.
     *
     * @receiver Mat de estado del filtro con al menos 4 filas.
     * @return [Rect] con las coordenadas extraídas del estado del filtro.
     */
    private fun Mat.toRect(): Rect {
        val x = get(0, 0)[0].toInt().coerceAtLeast(0)
        val y = get(1, 0)[0].toInt().coerceAtLeast(0)
        val r = get(2, 0)[0].toInt().coerceAtLeast(x + 4)
        val b = get(3, 0)[0].toInt().coerceAtLeast(y + 4)
        return Rect(x, y, r, b)
    }
}