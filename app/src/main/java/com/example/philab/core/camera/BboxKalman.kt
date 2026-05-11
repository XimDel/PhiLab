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
 * Internamente maneja 8 variables de estado: las 4 coordenadas del rectángulo
 * (left, top, right, bottom) y la velocidad de cada una (vL, vT, vR, vB).
 * Usa un modelo de velocidad constante donde cada posición se actualiza
 * sumando su velocidad correspondiente en cada timestep.
 *
 * El filtro opera en dos modos:
 * - **Con medición** ([update]): cuando el detector TFLite produce una detección,
 *   el filtro ejecuta predict → correct, combinando la predicción del modelo
 *   con la medición real para producir una estimación suavizada.
 * - **Sin medición** ([predict]): cuando no hay detección nueva (frames entre
 *   inferencias o pérdida temporal del objeto), el filtro predice la posición
 *   extrapolando con la velocidad estimada. Aplica un factor de decaimiento
 *   sobre la velocidad para evitar que el bbox se desplace indefinidamente.
 *   Si se superan [MAX_FRAMES_LOST] frames sin medición, el filtro se reinicia.
 *
 * Calibración de ruido:
 * - Ruido de proceso bajo en posiciones, mayor en velocidades: permite que
 *   el modelo se adapte a cambios de velocidad sin que las posiciones oscilen.
 * - Ruido de medición moderado-alto: absorbe el jitter típico del detector
 *   TFLite (~5-15px por frame) en vez de seguirlo fielmente.
 *
 * Todos los métodos públicos son `@Synchronized` para evitar race conditions
 * entre el hilo de análisis de CameraX y callbacks de la UI.
 */
class BboxKalman {

    private var kf: KalmanFilter? = null
    private var initialized = false
    private var framesLost = 0

    /**
     * Número máximo de fotogramas consecutivos sin medición antes de reiniciar el filtro.
     */
    val MAX_FRAMES_LOST = 8

    private val processNoise = 0.08f
    private val velocityNoise = 0.32f
    private val measurementNoise = 4.0f
    private val velocityDecay = 0.85f

    /**
     * Devuelve la instancia de [KalmanFilter] existente o crea y configura una nueva.
     *
     * La configuración incluye:
     * - Matriz de transición con modelo de velocidad constante (posición += velocidad).
     * - Matriz de medición que observa únicamente las cuatro coordenadas posicionales.
     * - Covarianza de proceso diferenciada entre posiciones ([processNoise]) y velocidades ([velocityNoise]).
     * - Covarianza de medición escalar ([measurementNoise]) aplicada a la identidad 4×4.
     * - Covarianza de error inicial con alta incertidumbre (escalar 10.0) sobre la identidad 8×8.
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

        val pnc = Mat.zeros(8, 8, CvType.CV_32F)
        pnc.put(0, 0, floatArrayOf(processNoise))
        pnc.put(1, 1, floatArrayOf(processNoise))
        pnc.put(2, 2, floatArrayOf(processNoise))
        pnc.put(3, 3, floatArrayOf(processNoise))
        pnc.put(4, 4, floatArrayOf(velocityNoise))
        pnc.put(5, 5, floatArrayOf(velocityNoise))
        pnc.put(6, 6, floatArrayOf(velocityNoise))
        pnc.put(7, 7, floatArrayOf(velocityNoise))
        newKf.set_processNoiseCov(pnc)
        pnc.release()

        val mnc = Mat.eye(4, 4, CvType.CV_32F)
        Core.setIdentity(mnc, Scalar(measurementNoise.toDouble()))
        newKf.set_measurementNoiseCov(mnc)
        mnc.release()

        val ecp = Mat.eye(8, 8, CvType.CV_32F)
        Core.setIdentity(ecp, Scalar(10.0))
        newKf.set_errorCovPost(ecp)
        ecp.release()

        kf = newKf
        return newKf
    }

    /**
     * Incorpora una medición válida al filtro y devuelve el bounding box corregido.
     *
     * Si el filtro aún no ha sido inicializado, inicializa el vector de estado con las
     * coordenadas del [bbox] recibido y velocidad cero. Reinicia el contador de
     * fotogramas perdidos a cero.
     *
     * @param bbox Rectángulo medido por el detector en coordenadas de imagen.
     * @return Rectángulo corregido por el filtro de Kalman.
     */
    @Synchronized
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
     * Predice la posición del bounding box sin incorporar ninguna medición.
     *
     * Incrementa el contador de fotogramas perdidos. Si supera [MAX_FRAMES_LOST],
     * invoca [reset] y devuelve `null`. De lo contrario, ejecuta la predicción del
     * filtro y aplica [velocityDecay] a los componentes de velocidad del estado
     * posterior para limitar la divergencia en ausencia de observaciones.
     *
     * @return Rectángulo predicho, o `null` si el filtro no está inicializado o se
     *         superó el número máximo de fotogramas sin medición.
     */
    @Synchronized
    fun predict(): Rect? {
        if (!initialized) return null
        framesLost++
        if (framesLost > MAX_FRAMES_LOST) {
            reset()
            return null
        }
        val filter = getOrCreateKf()
        val predicted = filter.predict()

        val state = filter.get_statePost()
        if (state.rows() >= 8) {
            for (i in 4..7) {
                val v = state.get(i, 0)[0].toFloat()
                state.put(i, 0, floatArrayOf(v * velocityDecay))
            }
            filter.set_statePost(state)
        }

        return predicted.toRect()
    }

    /**
     * Reinicia el filtro a su estado inicial, descartando toda estimación previa.
     */
    @Synchronized
    fun reset() {
        initialized = false
        framesLost = 0
        kf = null
    }

    /**
     * Indica si el filtro ha recibido al menos una medición y está listo para operar.
     *
     * @return `true` si el filtro está inicializado, `false` en caso contrario.
     */
    @Synchronized
    fun isInitialized() = initialized

    /**
     * Número de fotogramas consecutivos transcurridos sin recibir una medición válida.
     */
    val consecutiveFramesLost: Int get() = framesLost

    /**
     * Convierte este [Mat] de estado (columna 0, filas 0–3) a un [Rect] de Android.
     *
     * Los valores negativos se elevan a 0. El lado derecho e inferior se elevan al
     * menos a `x + 4` y `y + 4` respectivamente para garantizar un rectángulo no degenerado.
     */
    private fun Mat.toRect(): Rect {
        val x = get(0, 0)[0].toInt().coerceAtLeast(0)
        val y = get(1, 0)[0].toInt().coerceAtLeast(0)
        val r = get(2, 0)[0].toInt().coerceAtLeast(x + 4)
        val b = get(3, 0)[0].toInt().coerceAtLeast(y + 4)
        return Rect(x, y, r, b)
    }
}