package com.example.philab.domain.aruco

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.philab.data.aruco.ArucoDictionary
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ArucoGenerator {

    fun generateBitmap(markerId: Int, pixelSize: Int): Bitmap {
        val bits = ArucoDictionary.getBits(markerId)
        val gridSize = 6
        val cellSize = pixelSize.toFloat() / gridSize

        val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false }

        canvas.drawColor(Color.WHITE)

        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, pixelSize.toFloat(), pixelSize.toFloat(), paint)

        paint.color = Color.WHITE
        canvas.drawRect(cellSize, cellSize, cellSize * 5, cellSize * 5, paint)

        for (row in 0..3) {
            for (col in 0..3) {
                val bit = bits[row][col]
                paint.color = if (bit == 1) Color.BLACK else Color.WHITE
                val left   = (col + 1) * cellSize
                val top    = (row + 1) * cellSize
                val right  = left + cellSize
                val bottom = top + cellSize
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }

        return bitmap
    }

    fun exportPdf(
        context: Context,
        markerId: Int,
        sizeInCm: Int,
        onComplete: (success: Boolean, fileName: String) -> Unit
    ) {
        try {
            val pointsPerCm = 72f / 2.54f
            val markerPts   = (sizeInCm * pointsPerCm).toInt()
            val marginPts   = (1.5f * pointsPerCm).toInt()

            val pageWidth  = markerPts + marginPts * 2
            val pageHeight = markerPts + marginPts * 2 + (pointsPerCm * 3.5f).toInt()

            val bitmapSize = (sizeInCm / 2.54f * 300).toInt().coerceAtLeast(200)
            val bitmap = generateBitmap(markerId, bitmapSize)

            val pdfDocument = PdfDocument()
            val pageInfo    = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page        = pdfDocument.startPage(pageInfo)
            val canvas      = page.canvas
            val paint       = Paint()

            // White background
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

            // Marker
            val dst = android.graphics.RectF(
                marginPts.toFloat(),
                marginPts.toFloat(),
                (marginPts + markerPts).toFloat(),
                (marginPts + markerPts).toFloat()
            )
            canvas.drawBitmap(bitmap, null, dst, null)

            // Label: ID and size
            paint.isAntiAlias = true
            paint.textSize = (sizeInCm * 0.06f * pointsPerCm).coerceIn(6f, 14f)
            paint.color = Color.DKGRAY
            val labelY = marginPts + markerPts + paint.textSize * 1.5f
            canvas.drawText(
                "ArUco DICT_4X4_50 | ID: $markerId | ${sizeInCm}x${sizeInCm} cm",
                marginPts.toFloat(), labelY, paint
            )

            // Print warning
            val lineSpacing      = paint.textSize * 1.6f
            val warningTitleSize = paint.textSize * 0.85f
            val warningBodySize  = warningTitleSize * 0.92f

            paint.textSize = warningTitleSize
            paint.color = Color.DKGRAY
            canvas.drawText(
                "Al imprimir ten en cuenta:",
                marginPts.toFloat(), labelY + lineSpacing * 1.4f, paint
            )

            paint.textSize = warningBodySize
            paint.color = Color.DKGRAY
            canvas.drawText(
                "Ir a más ajustes / Escala:",
                marginPts.toFloat(), labelY + lineSpacing * 2.4f, paint
            )
            canvas.drawText(
                "- No usar opción: Ajustar al papel",
                marginPts.toFloat(), labelY + lineSpacing * 3.3f, paint
            )
            canvas.drawText(
                "- No usar opción: Ajustar al área imprimible",
                marginPts.toFloat(), labelY + lineSpacing * 4.2f, paint
            )
            canvas.drawText(
                "- Usar: Personalizada (100%)",
                marginPts.toFloat(), labelY + lineSpacing * 5.1f, paint
            )

            pdfDocument.finishPage(page)
            bitmap.recycle()

            val fileName = "aruco_4x4_50_id${markerId}_${sizeInCm}cm.pdf"

            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                uri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                downloadsDir.mkdirs()
                FileOutputStream(File(downloadsDir, fileName))
            }

            if (outputStream != null) {
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
                outputStream.close()
                pdfDocument.close()
                onComplete(true, fileName)
            } else {
                pdfDocument.close()
                onComplete(false, fileName)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false, "aruco_id${markerId}.pdf")
        }
    }
}