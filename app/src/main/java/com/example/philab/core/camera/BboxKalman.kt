package com.example.philab.core.camera

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.video.KalmanFilter

/**
 * Filtro de Kalman sobre el bounding box (x, y, right, bottom) + velocidades.
 * Usa android.graphics.Rect para no acoplar al resto del código con opencv.Rect.
 *
 * API Java OpenCV 4.x: set_X(mat) / get_X()
 * Mat.put(row, col, floatArrayOf(...)) — siempre array, nunca valor suelto.
 */
class BboxKalman {

    private var kf: KalmanFilter? = null
    private var initialized = false
    private var framesLost = 0

    val MAX_FRAMES_LOST = 15   // 0.5 s a 30 fps

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

    /** Llamar cuando hay medición válida. */
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

    /** Llamar durante oclusión. Predice con velocidad estimada. */
    fun predict(): Rect? {
        if (!initialized) return null
        framesLost++
        if (framesLost > MAX_FRAMES_LOST) {
            reset()
            return null
        }
        return getOrCreateKf().predict().toRect()
    }

    fun reset() {
        initialized = false
        framesLost = 0
        kf = null
    }

    fun isInitialized() = initialized

    private fun Mat.toRect(): Rect {
        val x = get(0, 0)[0].toInt().coerceAtLeast(0)
        val y = get(1, 0)[0].toInt().coerceAtLeast(0)
        val r = get(2, 0)[0].toInt().coerceAtLeast(x + 4)
        val b = get(3, 0)[0].toInt().coerceAtLeast(y + 4)
        return Rect(x, y, r, b)
    }
}