package com.example.philab.core.camera

import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.video.Video

class OpticalFlowTracker {

    sealed class TrackResult {
        data class Found(val bbox: Rect) : TrackResult()
        object Lost : TrackResult()
    }

    private var prevGray = Mat()
    private var trackedPoints = MatOfPoint2f()
    private var initialized = false

    private val lkWinSize = Size(21.0, 21.0)
    private val lkMaxLevel = 3
    private val lkCriteria = TermCriteria(
        TermCriteria.COUNT or TermCriteria.EPS, 20, 0.03
    )
    private val MIN_VALID_POINTS = 4

    fun init(gray: Mat, bbox: Rect) {
        reset()

        if (!isValid(gray)) return

        val safe = bbox.clamp(gray.cols(), gray.rows())
        if (safe.width < 8 || safe.height < 8) return

        val pts = borderPoints(safe)
        if (pts.size < MIN_VALID_POINTS) return

        trackedPoints = MatOfPoint2f(*pts.toTypedArray())
        prevGray = gray.clone()
        initialized = true
    }

    fun update(gray: Mat): TrackResult {
        if (!initialized) return TrackResult.Lost
        if (!isValid(gray)) {
            reset()
            return TrackResult.Lost
        }
        if (!isValid(prevGray)) {
            reset()
            return TrackResult.Lost
        }
        if (trackedPoints.rows() < MIN_VALID_POINTS) {
            reset()
            return TrackResult.Lost
        }

        if (gray.cols() != prevGray.cols() || gray.rows() != prevGray.rows()) {
            reset()
            return TrackResult.Lost
        }

        val nextPts = MatOfPoint2f()
        val status = MatOfByte()
        val err = MatOfFloat()

        try {
            Video.calcOpticalFlowPyrLK(
                prevGray,
                gray,
                trackedPoints,
                nextPts,
                status,
                err,
                lkWinSize,
                lkMaxLevel,
                lkCriteria
            )

            val statusArr = status.toArray()
            val nextArr = nextPts.toArray()

            if (statusArr.isEmpty() || nextArr.size < statusArr.size) {
                reset()
                return TrackResult.Lost
            }

            val valid = mutableListOf<Point>()
            for (i in statusArr.indices) {
                if (statusArr[i].toInt() != 1) continue
                val p = nextArr[i]
                if (p.x >= 0 && p.x < gray.cols() && p.y >= 0 && p.y < gray.rows()) {
                    valid.add(p)
                }
            }

            if (valid.size < MIN_VALID_POINTS) {
                reset()
                return TrackResult.Lost
            }

            val xs = valid.map { it.x }
            val ys = valid.map { it.y }

            val newBbox = Rect(
                xs.min().toInt().coerceAtLeast(0),
                ys.min().toInt().coerceAtLeast(0),
                (xs.max() - xs.min()).toInt().coerceAtLeast(4),
                (ys.max() - ys.min()).toInt().coerceAtLeast(4)
            ).clamp(gray.cols(), gray.rows())

            trackedPoints.release()
            trackedPoints = MatOfPoint2f(*valid.toTypedArray())

            if (!prevGray.empty()) prevGray.release()
            prevGray = gray.clone()

            return TrackResult.Found(newBbox)
        } catch (t: Throwable) {
            reset()
            return TrackResult.Lost
        } finally {
            nextPts.release()
            status.release()
            err.release()
        }
    }

    fun reset() {
        initialized = false

        try {
            trackedPoints.release()
        } catch (_: Throwable) {
        }
        trackedPoints = MatOfPoint2f()

        try {
            if (!prevGray.empty()) prevGray.release()
        } catch (_: Throwable) {
        }
        prevGray = Mat()
    }

    fun isActive() = initialized

    private fun isValid(mat: Mat): Boolean = try {
        !mat.empty() && mat.cols() > 0 && mat.rows() > 0 && mat.nativeObjAddr != 0L
    } catch (_: Throwable) {
        false
    }

    private fun borderPoints(bbox: Rect): List<Point> {
        val pts = mutableListOf<Point>()
        val step = maxOf(6, minOf(bbox.width, bbox.height) / 6)

        val x0 = bbox.x.toDouble()
        val y0 = bbox.y.toDouble()
        val x1 = (bbox.x + bbox.width).toDouble()
        val y1 = (bbox.y + bbox.height).toDouble()

        var x = x0
        while (x <= x1) {
            pts.add(Point(x, y0))
            pts.add(Point(x, y1))
            x += step
        }

        var y = y0 + step
        while (y < y1) {
            pts.add(Point(x0, y))
            pts.add(Point(x1, y))
            y += step
        }

        pts.add(Point((x0 + x1) / 2.0, (y0 + y1) / 2.0))

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