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

class ArucoScaleDetector(
    private val smoothingAlpha: Float = 0.20f,
    private val minMarkerSizePx: Float = 24f
) {

    private var smoothedCmPerPx: Float? = null
    private val dictionary by lazy { Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50) }
    private val detectorParameters by lazy { DetectorParameters() }
    private val detector by lazy { ArucoDetector(dictionary, detectorParameters) }
    private val rgbaMat by lazy { Mat() }

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

    fun reset() {
        smoothedCmPerPx = null
    }

    fun release() {
        if (rgbaMat.nativeObjAddr != 0L) rgbaMat.release()
    }

    private fun smooth(value: Float): Float {
        val previous = smoothedCmPerPx
        val smoothed = if (previous == null) value
        else previous + smoothingAlpha * (value - previous)
        smoothedCmPerPx = smoothed
        return smoothed
    }

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

    private fun estimateMarkerSidePx(cornerMat: Mat): Float {
        val points = extractCornerPoints(cornerMat)
        if (points.size < 4) return 0f
        val p0 = points[0]; val p1 = points[1]
        val p2 = points[2]; val p3 = points[3]
        val d01 = distance(p0, p1); val d12 = distance(p1, p2)
        val d23 = distance(p2, p3); val d30 = distance(p3, p0)
        return ((d01 + d12 + d23 + d30) / 4.0).toFloat()
    }

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

    private fun extractCornerOffsets(cornerMat: Mat): List<Offset> {
        return extractCornerPoints(cornerMat).map { p ->
            Offset(p.x.toFloat(), p.y.toFloat())
        }
    }

    private fun distance(a: Point, b: Point): Double {
        return hypot(b.x - a.x, b.y - a.y)
    }
}