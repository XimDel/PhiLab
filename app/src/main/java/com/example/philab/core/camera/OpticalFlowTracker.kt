package com.example.philab.core.camera

import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/**
 * VERSIÓN DIAGNÓSTICO — para aislar el punto exacto de fallo.
 *
 * Cambios vs versión anterior:
 *  - back-check DESACTIVADO completamente
 *  - MIN_VALID_POINTS = 1 (acepta cualquier punto que LK devuelva status=1)
 *  - Log.i en cada decisión crítica (visible sin filtro verbose)
 *  - Reporte del error LK (campo err) para saber si LK está corriendo
 *  - Reporte del tipo de feature cargada (Shi-Tomasi vs grilla)
 *
 * Si con MIN_VALID=1 y sin back-check sigue dando 0 found, el problema
 * es que LK devuelve status=0 para TODOS los puntos. Eso significa que:
 *   a) prevGray y gray son iguales (mismo frame clonado)
 *   b) los puntos están fuera de la imagen
 *   c) la ventana LK es demasiado grande para el bbox
 *   d) OpenCV no está inicializado cuando se llama LK
 */
class OpticalFlowTracker {

    sealed class TrackResult {
        data class Found(val bbox: Rect) : TrackResult()
        object Lost : TrackResult()
        object JustInitialized : TrackResult()
    }

    private var prevGray = Mat()
    private var trackedPoints = MatOfPoint2f()
    private var initialized = false
    private var justInited = false

    private var framesSinceInit = 0
    private var framesSinceFeatureReinit = 0

    val GRACE_FRAMES = 4
    val framesAfterInit: Int get() = framesSinceInit

    // Ventana más pequeña — menos exigente para texturas pobres
    private val lkWinSize = Size(15.0, 15.0)
    private val lkMaxLevel = 2
    private val lkCriteria = TermCriteria(
        TermCriteria.COUNT or TermCriteria.EPS, 20, 0.03
    )

    // DIAGNÓSTICO: bajado a 1 para ver si LK encuentra ALGO
    private val MIN_VALID_POINTS = 1

    private val REINIT_FEATURES_EVERY = 25

    private val MAX_FEATURES = 60
    private val QUALITY_LEVEL = 0.01
    private val MIN_DISTANCE = 4.0

    // DIAGNÓSTICO: back-check desactivado
    private val BACK_CHECK_ENABLED = false
    private val BACK_CHECK_SQ = 225.0

    private val cornersRaw = MatOfPoint()
    private val TAG = "OptFlowTracker"

    fun init(gray: Mat, bbox: Rect) {
        reset()
        if (!isValid(gray)) {
            Log.i(TAG, "INIT FAIL: gray inválido cols=${gray.cols()} rows=${gray.rows()}")
            return
        }

        val safe = bbox.clamp(gray.cols(), gray.rows())
        Log.i(TAG, "INIT: bbox original=$bbox → safe=$safe imageSize=${gray.cols()}x${gray.rows()}")

        if (safe.width < 10 || safe.height < 10) {
            Log.i(TAG, "INIT FAIL: bbox demasiado pequeño ${safe.width}x${safe.height}")
            return
        }

        val strategy = loadFeaturesDetailed(gray, safe)
        Log.i(TAG, "INIT: feature strategy=$strategy pts=${trackedPoints.rows()}")

        if (trackedPoints.rows() < MIN_VALID_POINTS) {
            Log.i(TAG, "INIT FAIL: insuficientes puntos ${trackedPoints.rows()}")
            return
        }

        prevGray = gray.clone()
        initialized = true
        justInited = true
        framesSinceInit = 0
        framesSinceFeatureReinit = 0
        Log.i(TAG, "INIT OK: ${trackedPoints.rows()} puntos listos")
    }

    fun update(gray: Mat): TrackResult {
        if (!initialized) return TrackResult.Lost

        framesSinceInit++

        if (justInited) {
            justInited = false
            Log.i(TAG, "UPDATE frame=$framesSinceInit: JustInitialized")
            return TrackResult.JustInitialized
        }

        if (!isValid(gray) || !isValid(prevGray)) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL mat inválido")
            reset(); return TrackResult.Lost
        }

        val ptCount = trackedPoints.rows()
        if (ptCount < MIN_VALID_POINTS) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL pocos puntos $ptCount")
            reset(); return TrackResult.Lost
        }

        val sameSize = gray.cols() == prevGray.cols() && gray.rows() == prevGray.rows()
        if (!sameSize) {
            Log.i(TAG, "UPDATE frame=$framesSinceInit: FAIL tamaño cambió ${prevGray.cols()}x${prevGray.rows()} → ${gray.cols()}x${gray.rows()}")
            reset(); return TrackResult.Lost
        }

        Log.i(TAG, "UPDATE frame=$framesSinceInit: corriendo LK con $ptCount puntos, img=${gray.cols()}x${gray.rows()}")

        val nextPts    = MatOfPoint2f()
        val status     = MatOfByte()
        val err        = MatOfFloat()
        val ptsBack    = MatOfPoint2f()
        val statusBack = MatOfByte()
        val errBack    = MatOfFloat()

        try {
            Video.calcOpticalFlowPyrLK(
                prevGray, gray, trackedPoints, nextPts,
                status, err, lkWinSize, lkMaxLevel, lkCriteria
            )

            val fwdStatus = status.toArray()
            val fwdNext   = nextPts.toArray()
            val fwdPrev   = trackedPoints.toArray()
            val fwdErr    = err.toArray()

            Log.i(TAG, "LK result: statusArr=${fwdStatus.size} nextArr=${fwdNext.size} errArr=${fwdErr.size}")

            if (fwdStatus.isEmpty()) {
                Log.i(TAG, "LK FAIL: status array vacío → Lost")
                reset(); return TrackResult.Lost
            }

            val statusOk  = fwdStatus.count { it.toInt() == 1 }
            val statusFail = fwdStatus.count { it.toInt() == 0 }
            val avgErr = if (fwdErr.isNotEmpty()) fwdErr.map { it.toDouble() }.average() else -1.0
            Log.i(TAG, "LK status: OK=$statusOk FAIL=$statusFail avgErr=${"%.2f".format(avgErr)}")

            // Log primeros puntos para ver si están dentro de la imagen
            fwdPrev.take(3).forEachIndexed { i, p ->
                val s = if (i < fwdStatus.size) fwdStatus[i].toInt() else -1
                val n = if (i < fwdNext.size) fwdNext[i] else Point(-1.0, -1.0)
                Log.i(TAG, "  pt[$i] prev=(${p.x.toInt()},${p.y.toInt()}) → next=(${n.x.toInt()},${n.y.toInt()}) status=$s")
            }

            val valid = mutableListOf<Point>()
            var backFail = 0
            var outOfBounds = 0

            for (i in fwdStatus.indices) {
                if (fwdStatus[i].toInt() != 1) continue

                // DIAGNÓSTICO: back-check desactivado
                if (BACK_CHECK_ENABLED && i < fwdNext.size) {
                    // (omitido en esta versión diagnóstico)
                }

                val p = if (i < fwdNext.size) fwdNext[i] else continue
                if (p.x >= 0 && p.x < gray.cols() && p.y >= 0 && p.y < gray.rows()) {
                    valid.add(p)
                } else {
                    outOfBounds++
                }
            }

            Log.i(TAG, "valid=${valid.size} outOfBounds=$outOfBounds backFail=$backFail MIN=$MIN_VALID_POINTS")

            if (valid.size < MIN_VALID_POINTS) {
                Log.i(TAG, "RESULT: Lost (valid=${valid.size})")
                reset(); return TrackResult.Lost
            }

            val xs = valid.map { it.x }
            val ys = valid.map { it.y }

            val newBbox = Rect(
                xs.min().toInt().coerceAtLeast(0),
                ys.min().toInt().coerceAtLeast(0),
                (xs.max() - xs.min()).toInt().coerceAtLeast(8),
                (ys.max() - ys.min()).toInt().coerceAtLeast(8)
            ).clamp(gray.cols(), gray.rows())

            framesSinceFeatureReinit++
            if (framesSinceFeatureReinit >= REINIT_FEATURES_EVERY) {
                framesSinceFeatureReinit = 0
                loadFeaturesDetailed(gray, newBbox)
            } else {
                trackedPoints.release()
                trackedPoints = MatOfPoint2f(*valid.toTypedArray())
            }

            if (!prevGray.empty()) prevGray.release()
            prevGray = gray.clone()

            Log.i(TAG, "RESULT: Found bbox=$newBbox")
            return TrackResult.Found(newBbox)

        } catch (t: Throwable) {
            Log.e(TAG, "UPDATE EXCEPCIÓN: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            reset(); return TrackResult.Lost
        } finally {
            nextPts.release();    status.release();     err.release()
            ptsBack.release();    statusBack.release(); errBack.release()
        }
    }

    fun reset() {
        initialized = false
        justInited = false
        framesSinceInit = 0
        framesSinceFeatureReinit = 0
        try { trackedPoints.release() } catch (_: Throwable) {}
        trackedPoints = MatOfPoint2f()
        try { if (!prevGray.empty()) prevGray.release() } catch (_: Throwable) {}
        prevGray = Mat()
    }

    fun isActive() = initialized

    /**
     * Carga features y retorna el nombre de la estrategia usada para el log.
     */
    private fun loadFeaturesDetailed(gray: Mat, bbox: Rect): String {
        // Intento 1: Shi-Tomasi interior
        val roi = Mat(gray, bbox)
        cornersRaw.release()
        var shiTomasiCount = 0
        try {
            Imgproc.goodFeaturesToTrack(roi, cornersRaw, MAX_FEATURES, QUALITY_LEVEL, MIN_DISTANCE)
            shiTomasiCount = cornersRaw.rows()
            Log.i(TAG, "  Shi-Tomasi en bbox $bbox → $shiTomasiCount corners")
        } catch (e: Throwable) {
            Log.e(TAG, "  Shi-Tomasi EXCEPCIÓN: ${e.message}")
        } finally {
            roi.release()
        }

        if (shiTomasiCount >= MIN_VALID_POINTS) {
            val arr = cornersRaw.toArray()
            val shifted = Array(arr.size) { i -> Point(arr[i].x + bbox.x, arr[i].y + bbox.y) }
            try { trackedPoints.release() } catch (_: Throwable) {}
            trackedPoints = MatOfPoint2f(*shifted)
            return "shi-tomasi($shiTomasiCount)"
        }

        // Intento 2: anillo de borde expandido
        val expandX = (bbox.width * 0.25).toInt().coerceAtLeast(6)
        val expandY = (bbox.height * 0.25).toInt().coerceAtLeast(6)
        val ringBbox = Rect(
            (bbox.x - expandX).coerceAtLeast(0),
            (bbox.y - expandY).coerceAtLeast(0),
            (bbox.width + 2 * expandX).coerceAtMost(gray.cols() - (bbox.x - expandX).coerceAtLeast(0)),
            (bbox.height + 2 * expandY).coerceAtMost(gray.rows() - (bbox.y - expandY).coerceAtLeast(0))
        )
        val roiRing = Mat(gray, ringBbox)
        cornersRaw.release()
        var ringCount = 0
        try {
            Imgproc.goodFeaturesToTrack(roiRing, cornersRaw, MAX_FEATURES, QUALITY_LEVEL, MIN_DISTANCE)
            ringCount = cornersRaw.rows()
            Log.i(TAG, "  Shi-Tomasi en ring $ringBbox → $ringCount corners")
        } catch (e: Throwable) {
            Log.e(TAG, "  Shi-Tomasi ring EXCEPCIÓN: ${e.message}")
        } finally {
            roiRing.release()
        }

        if (ringCount >= MIN_VALID_POINTS) {
            val arr = cornersRaw.toArray()
            val shifted = Array(arr.size) { i -> Point(arr[i].x + ringBbox.x, arr[i].y + ringBbox.y) }
            try { trackedPoints.release() } catch (_: Throwable) {}
            trackedPoints = MatOfPoint2f(*shifted)
            return "ring-shi-tomasi($ringCount)"
        }

        // Intento 3: grilla en borde del bbox
        val borderPts = borderGridPoints(bbox, gray.cols(), gray.rows())
        Log.i(TAG, "  Grilla borde → ${borderPts.size} puntos")
        try { trackedPoints.release() } catch (_: Throwable) {}
        trackedPoints = MatOfPoint2f(*borderPts.toTypedArray())
        return "border-grid(${borderPts.size})"
    }

    private fun borderGridPoints(bbox: Rect, imgW: Int, imgH: Int): List<Point> {
        val pts = mutableListOf<Point>()
        val steps = 8
        // Borde superior e inferior
        for (i in 0 until steps) {
            val x = bbox.x + bbox.width.toDouble() * i / (steps - 1)
            listOf(bbox.y.toDouble(), (bbox.y + bbox.height - 1).toDouble()).forEach { y ->
                if (x in 0.0..(imgW - 1).toDouble() && y in 0.0..(imgH - 1).toDouble())
                    pts.add(Point(x, y))
            }
        }
        // Borde izquierdo y derecho
        for (i in 1 until steps - 1) {
            val y = bbox.y + bbox.height.toDouble() * i / (steps - 1)
            listOf(bbox.x.toDouble(), (bbox.x + bbox.width - 1).toDouble()).forEach { x ->
                if (x in 0.0..(imgW - 1).toDouble() && y in 0.0..(imgH - 1).toDouble())
                    pts.add(Point(x, y))
            }
        }
        return pts
    }

    private fun isValid(mat: Mat): Boolean = try {
        !mat.empty() && mat.cols() > 0 && mat.rows() > 0 && mat.nativeObjAddr != 0L
    } catch (_: Throwable) { false }

    private fun Rect.clamp(w: Int, h: Int): Rect {
        val x1 = x.coerceIn(0, (w - 1).coerceAtLeast(0))
        val y1 = y.coerceIn(0, (h - 1).coerceAtLeast(0))
        val x2 = (x + width).coerceIn(x1 + 1, w)
        val y2 = (y + height).coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
    }
}