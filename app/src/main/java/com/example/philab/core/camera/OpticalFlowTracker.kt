package com.example.philab.core.camera

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
 * OpticalFlowTracker — Lucas-Kanade sparse con Shi-Tomasi corner detection.
 *
 * Versión estable — la que producía tracking visible con FPS 23-24.
 *
 * Parámetros clave:
 *   QUALITY_LEVEL 0.01  — acepta corners de baja calidad (objetos lisos)
 *   BLOCK_SIZE    7     — mejor promedio en zonas sin gradiente fuerte
 *   MAX_FEATURES  50    — más puntos iniciales
 *   MIN_DISTANCE  4.0   — mayor densidad de puntos
 *   MIN_VALID_POINTS 3  — más tolerante antes de declarar Lost
 *   lkWinSize 31×31     — ventana más grande para objetos rápidos
 *   Grilla fallback 6×6 — 36 puntos uniformes cuando Shi-Tomasi no encuentra features
 *   Back-check dinámico — umbral escala con velocidad del objeto (max 64px², 4×mediana²)
 *   justInited flag     — evita correr LK en el mismo frame del init
 */
class OpticalFlowTracker {

    sealed class TrackResult {
        data class Found(val bbox: Rect) : TrackResult()
        object Lost : TrackResult()
        object JustInitialized : TrackResult()
    }

    private var prevGray      = Mat()
    private var trackedPoints = MatOfPoint2f()
    private var initialized   = false
    private var justInited    = false

    private val lkWinSize  = Size(31.0, 31.0)
    private val lkMaxLevel = 3
    private val lkCriteria = TermCriteria(
        TermCriteria.COUNT or TermCriteria.EPS, 30, 0.01
    )
    private val MIN_VALID_POINTS = 3

    private val MAX_FEATURES  = 50
    private val QUALITY_LEVEL = 0.01
    private val MIN_DISTANCE  = 4.0
    private val BLOCK_SIZE    = 7

    private val cornersRaw = MatOfPoint()

    fun init(gray: Mat, bbox: Rect) {
        reset()
        if (!isValid(gray)) return

        val safe = bbox.clamp(gray.cols(), gray.rows())
        if (safe.width < 10 || safe.height < 10) return

        val roi = Mat(gray, safe)
        cornersRaw.release()

        var foundCorners = false
        try {
            Imgproc.goodFeaturesToTrack(
                roi, cornersRaw,
                MAX_FEATURES, QUALITY_LEVEL, MIN_DISTANCE,
                Mat(), BLOCK_SIZE, false, 0.04
            )
            foundCorners = cornersRaw.rows() >= MIN_VALID_POINTS
        } catch (_: Throwable) {
        } finally {
            roi.release()
        }

        if (foundCorners) {
            val arr     = cornersRaw.toArray()
            val shifted = Array(arr.size) { i ->
                Point(arr[i].x + safe.x, arr[i].y + safe.y)
            }
            trackedPoints = MatOfPoint2f(*shifted)
        } else {
            val pts = gridPoints(safe)
            if (pts.size < MIN_VALID_POINTS) return
            trackedPoints = MatOfPoint2f(*pts.toTypedArray())
        }

        prevGray    = gray.clone()
        initialized = true
        justInited  = true
    }

    fun update(gray: Mat): TrackResult {
        if (!initialized) return TrackResult.Lost

        if (justInited) {
            justInited = false
            return TrackResult.JustInitialized
        }

        if (!isValid(gray) || !isValid(prevGray)) { reset(); return TrackResult.Lost }
        if (trackedPoints.rows() < MIN_VALID_POINTS) { reset(); return TrackResult.Lost }
        if (gray.cols() != prevGray.cols() || gray.rows() != prevGray.rows()) {
            reset(); return TrackResult.Lost
        }

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

            if (fwdStatus.isEmpty() || fwdNext.size < fwdStatus.size) {
                reset(); return TrackResult.Lost
            }

            Video.calcOpticalFlowPyrLK(
                gray, prevGray, nextPts, ptsBack,
                statusBack, errBack, lkWinSize, lkMaxLevel, lkCriteria
            )
            val bwdStatus = statusBack.toArray()
            val bwdPrev   = ptsBack.toArray()

            // Umbral dinámico: escala con la velocidad mediana del objeto
            val fwdDistancesSq = fwdStatus.indices
                .filter { i ->
                    fwdStatus[i].toInt() == 1 &&
                            i < fwdNext.size && i < fwdPrev.size
                }
                .map { i ->
                    val dx = fwdNext[i].x - fwdPrev[i].x
                    val dy = fwdNext[i].y - fwdPrev[i].y
                    dx * dx + dy * dy
                }
                .sorted()

            val medianMoveSq      = if (fwdDistancesSq.isNotEmpty())
                fwdDistancesSq[fwdDistancesSq.size / 2] else 0.0
            val backCheckThreshSq = maxOf(64.0, medianMoveSq * 4.0)

            val valid = mutableListOf<Point>()
            for (i in fwdStatus.indices) {
                if (fwdStatus[i].toInt() != 1) continue
                if (i < bwdStatus.size && bwdStatus[i].toInt() == 1 &&
                    i < bwdPrev.size && i < fwdPrev.size) {
                    val dx = bwdPrev[i].x - fwdPrev[i].x
                    val dy = bwdPrev[i].y - fwdPrev[i].y
                    if (dx * dx + dy * dy > backCheckThreshSq) continue
                }
                val p = fwdNext[i]
                if (p.x >= 0 && p.x < gray.cols() && p.y >= 0 && p.y < gray.rows()) {
                    valid.add(p)
                }
            }

            if (valid.size < MIN_VALID_POINTS) { reset(); return TrackResult.Lost }

            val xs = valid.map { it.x }
            val ys = valid.map { it.y }

            val newBbox = Rect(
                xs.min().toInt().coerceAtLeast(0),
                ys.min().toInt().coerceAtLeast(0),
                (xs.max() - xs.min()).toInt().coerceAtLeast(8),
                (ys.max() - ys.min()).toInt().coerceAtLeast(8)
            ).clamp(gray.cols(), gray.rows())

            trackedPoints.release()
            trackedPoints = MatOfPoint2f(*valid.toTypedArray())
            if (!prevGray.empty()) prevGray.release()
            prevGray = gray.clone()

            return TrackResult.Found(newBbox)

        } catch (t: Throwable) {
            reset(); return TrackResult.Lost
        } finally {
            nextPts.release();    status.release();     err.release()
            ptsBack.release();    statusBack.release(); errBack.release()
        }
    }

    fun reset() {
        initialized = false
        justInited  = false
        try { trackedPoints.release() } catch (_: Throwable) {}
        trackedPoints = MatOfPoint2f()
        try { if (!prevGray.empty()) prevGray.release() } catch (_: Throwable) {}
        prevGray = Mat()
    }

    fun isActive() = initialized

    private fun isValid(mat: Mat): Boolean = try {
        !mat.empty() && mat.cols() > 0 && mat.rows() > 0 && mat.nativeObjAddr != 0L
    } catch (_: Throwable) { false }

    private fun gridPoints(bbox: Rect): List<Point> {
        val pts   = mutableListOf<Point>()
        val cols  = 6; val rows = 6
        val stepX = bbox.width.toDouble()  / (cols + 1)
        val stepY = bbox.height.toDouble() / (rows + 1)
        for (r in 1..rows) for (c in 1..cols) {
            pts.add(Point(bbox.x + c * stepX, bbox.y + r * stepY))
        }
        return pts
    }

    private fun Rect.clamp(w: Int, h: Int): Rect {
        val x1 = x.coerceIn(0, (w - 1).coerceAtLeast(0))
        val y1 = y.coerceIn(0, (h - 1).coerceAtLeast(0))
        val x2 = (x + width).coerceIn(x1 + 1, w)
        val y2 = (y + height).coerceIn(y1 + 1, h)
        return Rect(x1, y1, x2 - x1, y2 - y1)
    }
}