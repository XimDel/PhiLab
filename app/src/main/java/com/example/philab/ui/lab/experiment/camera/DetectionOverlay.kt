package com.example.philab.ui.lab.experiment.camera

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import com.example.philab.core.camera.UiDetection
import com.example.philab.core.measurement.MeasurementResult

@Composable
fun DetectionOverlay(
    detections: List<UiDetection>,
    viewSize: IntSize,
    measurementResult: MeasurementResult? = null,
    selectedLabel: String? = null
) {
    if (viewSize.width == 0 || viewSize.height == 0) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (detections.isEmpty()) return@Canvas

        val srcW = detections.first().sourceWidth.toFloat()
        val srcH = detections.first().sourceHeight.toFloat()
        val dstW = viewSize.width.toFloat()
        val dstH = viewSize.height.toFloat()

        val scale = max(dstW / srcW, dstH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val dx = (dstW - scaledW) / 2f
        val dy = (dstH - scaledH) / 2f

        fun mapX(x: Float) = x * scale + dx
        fun mapY(y: Float) = y * scale + dy

        // Paints para detecciones normales
        val textPaint = Paint().apply {
            color = Color.White.toArgb(); isAntiAlias = true
            textSize = 28f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val dimPaint = Paint().apply {
            color = Color(0.9f, 0.9f, 0.9f, 1f).toArgb(); isAntiAlias = true
            textSize = 24f; typeface = android.graphics.Typeface.DEFAULT
        }
        val bgPaint = Paint().apply {
            color = Color(0f, 0f, 0f, 0.55f).toArgb(); isAntiAlias = true
        }

        // Paints para el objeto seleccionado
        val selectedTextPaint = Paint().apply {
            color = Color(0xFF111111).toArgb(); isAntiAlias = true
            textSize = 28f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val selectedBadgePaint = Paint().apply {
            color = Color(0xFFFFD600).toArgb(); isAntiAlias = true
        }
        val selectedDimPaint = Paint().apply {
            color = Color(0xFF333333).toArgb(); isAntiAlias = true
            textSize = 22f; typeface = android.graphics.Typeface.DEFAULT
        }

        detections.forEachIndexed { index, d ->
            val left   = mapX(d.left).coerceIn(0f, size.width)
            val top    = mapY(d.top).coerceIn(0f, size.height)
            val right  = mapX(d.right).coerceIn(0f, size.width)
            val bottom = mapY(d.bottom).coerceIn(0f, size.height)

            if (right <= left || bottom <= top) return@forEachIndexed

            // Amarillo y más grueso si es seleccionado, verde normal
            val boxColor = if (d.isSelected) Color(0xFFFFD600) else Color.Green
            val boxStroke = if (d.isSelected) 6f else 4f

            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = boxStroke)
            )

            // Centroide
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            drawCircle(
                color = if (d.isSelected) Color(0xFFFFD600) else Color.Red,
                radius = if (d.isSelected) 10f else 8f,
                center = Offset(centerX, centerY)
            )

            val activeLabelPaint = if (d.isSelected) selectedTextPaint else textPaint
            val activeBgPaint    = if (d.isSelected) selectedBadgePaint else bgPaint
            val activeDimPaint   = if (d.isSelected) selectedDimPaint else dimPaint

            val displayLabel = if (d.isSelected && selectedLabel != null) selectedLabel else d.label
            val labelText = "$displayLabel ${(d.score * 100).toInt()}%"
            val selectedTag = if (d.isSelected) "★ Objeto seleccionado" else null

            val labelWidth = activeLabelPaint.measureText(labelText)
            val labelFm = activeLabelPaint.fontMetrics
            val labelH = labelFm.descent - labelFm.ascent
            val paddingX = 12f; val paddingY = 6f

            val dimText = if ((d.isSelected || index == 0) && measurementResult != null) {
                "${"%.1f".format(measurementResult.widthCm)} × ${"%.1f".format(measurementResult.heightCm)} cm"
            } else null

            val tagWidth = if (selectedTag != null) activeDimPaint.measureText(selectedTag) else 0f
            val tagFm = activeDimPaint.fontMetrics
            val tagH = if (selectedTag != null) tagFm.descent - tagFm.ascent else 0f
            val tagPaddingTop = if (selectedTag != null) 3f else 0f

            val dimWidth = if (dimText != null) activeDimPaint.measureText(dimText) else 0f
            val dimFm = activeDimPaint.fontMetrics
            val dimH = if (dimText != null) dimFm.descent - dimFm.ascent else 0f
            val dimPaddingTop = if (dimText != null) 4f else 0f

            val bgW = maxOf(labelWidth, dimWidth, tagWidth) + paddingX * 2f
            val bgH = labelH + paddingY * 2f +
                    (if (selectedTag != null) tagH + tagPaddingTop else 0f) +
                    (if (dimText != null) dimH + dimPaddingTop else 0f)

            val bgLeft = left
            val bgTopPreferred = top - bgH - 2f
            val bgTop = if (bgTopPreferred >= 0f) bgTopPreferred else top + 4f
            val bgRight = (bgLeft + bgW).coerceAtMost(size.width)
            val bgBottom = bgTop + bgH

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 12f, 12f, activeBgPaint)

                val textX = bgLeft + paddingX
                var currentY = bgTop + paddingY - labelFm.ascent
                nc.drawText(labelText, textX, currentY, activeLabelPaint)
                currentY += labelFm.descent

                if (selectedTag != null) {
                    currentY += tagPaddingTop - tagFm.ascent
                    nc.drawText(selectedTag, textX, currentY, activeDimPaint)
                    currentY += tagFm.descent
                }

                if (dimText != null) {
                    currentY += dimPaddingTop - dimFm.ascent
                    nc.drawText(dimText, textX, currentY, activeDimPaint)
                }
            }
        }
    }
}