package com.example.philab.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.ui.history.HistoryScreen
import com.example.philab.ui.theme.PhiLabTheme
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta ExperimentResults a PDF usando PdfDocument nativo de Android.
 * Sin dependencias externas.
 *
 * Diseño: A4 vertical (595 x 842 pt), paleta oscura con acentos verdes,
 * header con título, secciones con fondo redondeado, tabla con filas alternas.
 */
object PdfExporter {

    data class PdfOptions(
        val includeFecha: Boolean      = true,
        val includeDuracion: Boolean   = true,
        val includeMuestras: Boolean   = false,
        val includeFrecuencia: Boolean = false,
        val includeEscala: Boolean     = false,
        val includeUnidad: Boolean     = false,
        val includeObjeto: Boolean     = false,
        val includeResumen: Boolean    = true,
        val includeTabla: Boolean      = true,
    )

    // ── Dimensiones A4 en puntos (72 dpi) ────────────────────────────────────
    private const val PAGE_W   = 595f
    private const val PAGE_H   = 842f
    private const val MARGIN   = 36f
    private const val CONTENT_W = PAGE_W - MARGIN * 2f

    // ── Paleta ────────────────────────────────────────────────────────────────
    private val COL_BG_PAGE    = Color.parseColor("#12121F")
    private val COL_BG_HEADER  = Color.parseColor("#1A1A2E")
    private val COL_BG_SECTION = Color.parseColor("#1E1E35")
    private val COL_BG_ROW_A   = Color.parseColor("#1A1A2E")
    private val COL_BG_ROW_B   = Color.parseColor("#16162A")
    private val COL_ACCENT     = Color.parseColor("#26D9A0")
    private val COL_ACCENT2    = Color.parseColor("#4FC3F7")
    private val COL_TEXT       = Color.parseColor("#FFFFFF")
    private val COL_TEXT_SEC   = Color.parseColor("#9090B0")
    private val COL_DIVIDER    = Color.parseColor("#252540")

    // ── Fuentes ───────────────────────────────────────────────────────────────
    private val FONT_BOLD    = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val FONT_NORMAL  = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    /**
     * Genera y guarda el PDF en Downloads.
     * Devuelve true si tuvo éxito.
     */
    fun saveToDownloads(
        context: Context,
        results: ExperimentResults,
        options: PdfOptions,
        fileName: String = buildFileName(results)
    ): Boolean {
        val doc = PdfDocument()
        return try {
            buildDocument(doc, results, options)
            val stream = openOutputStream(context, fileName) ?: return false
            stream.use { doc.writeTo(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            doc.close()
        }
    }

    internal fun buildFileName(results: ExperimentResults): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(results.recordedAt))
        val label = results.selectedLabel
            .replace(" ", "_")
            .replace("[^a-zA-Z0-9_]".toRegex(), "")
            .take(20)
        return "PhiLab_${label}_$ts.pdf"
    }

    // ── Construcción del documento ────────────────────────────────────────────

    private fun buildDocument(
        doc: PdfDocument,
        results: ExperimentResults,
        options: PdfOptions
    ) {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())
        val unit = results.unit

        // Acumulamos bloques de contenido y los paginamos
        val renderer = PageRenderer(doc)

        // ── Portada / header ──────────────────────────────────────────────────
        renderer.drawPageBackground()
        renderer.drawAppHeader(results.selectedLabel, dateFormatter.format(Date(results.recordedAt)))

        // ── Metadata ──────────────────────────────────────────────────────────
        val metaRows = mutableListOf<Pair<String, String>>()
        if (options.includeObjeto)     metaRows += "Objeto" to results.selectedLabel
        if (options.includeFecha)      metaRows += "Fecha" to dateFormatter.format(Date(results.recordedAt))
        if (options.includeDuracion)   metaRows += "Duracion" to formatDuration(results.durationMs)
        if (options.includeMuestras)   metaRows += "Muestras" to "${results.sampleCount} pts"
        if (options.includeFrecuencia) metaRows += "Frecuencia" to "${"%.1f".format(results.sampleRateHz)} Hz"
        if (options.includeUnidad)     metaRows += "Unidad" to unit
        if (options.includeEscala && results.isCalibrated)
            metaRows += "Escala" to "${"%.4f".format(results.cmPerPx)} cm/px"

        if (metaRows.isNotEmpty()) {
            renderer.drawSectionTitle("INFORMACION DE LA SESION")
            renderer.drawKeyValueCard(metaRows)
        }

        // ── Resumen cinematico ─────────────────────────────────────────────────
        if (options.includeResumen) {
            renderer.drawSectionTitle("RESULTADOS CINEMATICOS")
            renderer.drawKpiGrid(
                listOf(
                    Triple("Distancia total",   "${"%.2f".format(results.totalDistanceCm)} $unit",  COL_ACCENT),
                    Triple("Desplazamiento",     "${"%.2f".format(results.displacementCm)} $unit",   COL_ACCENT2),
                    Triple("Velocidad media",    "${"%.2f".format(results.avgSpeedCmS)} $unit/s",    COL_ACCENT),
                    Triple("Aceleracion media",  "${"%.2f".format(results.avgAccelCmS2)} $unit/s2",  COL_ACCENT2),
                )
            )
        }

        // ── Tabla de datos ────────────────────────────────────────────────────
        if (options.includeTabla && results.points.isNotEmpty()) {
            renderer.drawSectionTitle("DATOS CAPTURADOS  (${results.points.size} puntos)")
            renderer.drawTable(
                headers = listOf("#", "t (s)", "x ($unit)", "y ($unit)"),
                rows    = results.points.mapIndexed { i, pt ->
                    listOf(
                        "${i + 1}",
                        "%.3f".format(pt.tSeconds),
                        "%.2f".format(pt.xCm),
                        "%.2f".format(pt.yCm)
                    )
                }
            )
        }

        // ── Footer en última página ───────────────────────────────────────────
        renderer.drawFooter()
        renderer.finishPage()
    }

    // ── PageRenderer — gestiona cursor Y y paginación automática ─────────────

    private class PageRenderer(private val doc: PdfDocument) {
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var cursorY: Float = MARGIN

        // Paints reutilizables
        private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COL_TEXT }
        private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ── Página ────────────────────────────────────────────────────────────

        private fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(
                PAGE_W.toInt(), PAGE_H.toInt(), doc.pages.size + 1
            ).create()
            return doc.startPage(info)
        }

        /** Asegura que hay espacio; si no, crea nueva página. */
        private fun ensureSpace(needed: Float) {
            if (cursorY + needed > PAGE_H - MARGIN - 20f) {
                drawFooter()
                finishPage()
                page = newPage()
                canvas = page.canvas
                cursorY = MARGIN
                drawPageBackground()
            }
        }

        fun finishPage() {
            doc.finishPage(page)
        }

        // ── Fondo ─────────────────────────────────────────────────────────────

        fun drawPageBackground() {
            bgPaint.color = COL_BG_PAGE
            canvas.drawRect(0f, 0f, PAGE_W, PAGE_H, bgPaint)
        }

        // ── Header principal ──────────────────────────────────────────────────

        fun drawAppHeader(label: String, date: String) {
            // Banda de header
            bgPaint.color = COL_BG_HEADER
            canvas.drawRect(0f, 0f, PAGE_W, 80f, bgPaint)

            // Línea acento
            bgPaint.color = COL_ACCENT
            canvas.drawRect(0f, 80f, PAGE_W, 83f, bgPaint)

            // Título app
            textPaint.apply {
                typeface  = FONT_BOLD
                textSize  = 20f
                color     = COL_ACCENT
            }
            canvas.drawText("PhiLab", MARGIN, 34f, textPaint)

            // Subtítulo
            textPaint.apply {
                typeface  = FONT_NORMAL
                textSize  = 10f
                color     = COL_TEXT_SEC
            }
            canvas.drawText("Reporte de experimento", MARGIN, 50f, textPaint)

            // Objeto y fecha — derecha
            textPaint.apply {
                textSize  = 10f
                color     = COL_TEXT
                typeface  = FONT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(label, PAGE_W - MARGIN, 34f, textPaint)
            textPaint.apply {
                typeface  = FONT_NORMAL
                color     = COL_TEXT_SEC
            }
            canvas.drawText(date, PAGE_W - MARGIN, 50f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT

            cursorY = 100f
        }

        // ── Título de sección ─────────────────────────────────────────────────

        fun drawSectionTitle(title: String) {
            ensureSpace(28f)
            textPaint.apply {
                typeface  = FONT_BOLD
                textSize  = 9f
                color     = COL_ACCENT
            }
            canvas.drawText(title, MARGIN, cursorY + 12f, textPaint)
            // Línea decorativa
            bgPaint.color = COL_ACCENT
            canvas.drawRect(MARGIN, cursorY + 16f, MARGIN + 32f, cursorY + 17.5f, bgPaint)
            cursorY += 26f
        }

        // ── Card de pares clave-valor ─────────────────────────────────────────

        fun drawKeyValueCard(rows: List<Pair<String, String>>) {
            val rowH   = 22f
            val totalH = rows.size * rowH + 8f
            ensureSpace(totalH + 8f)

            // Fondo de la card
            rectPaint.color = COL_BG_SECTION
            val rect = RectF(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + totalH)
            canvas.drawRoundRect(rect, 8f, 8f, rectPaint)

            rows.forEachIndexed { i, (key, value) ->
                val y = cursorY + 8f + (i + 1) * rowH - 6f

                // Divider entre filas
                if (i > 0) {
                    bgPaint.color = COL_DIVIDER
                    canvas.drawRect(
                        MARGIN + 12f, cursorY + 8f + i * rowH,
                        MARGIN + CONTENT_W - 12f, cursorY + 8f + i * rowH + 0.5f,
                        bgPaint
                    )
                }

                textPaint.apply { typeface = FONT_NORMAL; textSize = 10f; color = COL_TEXT_SEC }
                canvas.drawText(key, MARGIN + 12f, y, textPaint)

                textPaint.apply {
                    typeface  = FONT_BOLD
                    color     = COL_TEXT
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(value, MARGIN + CONTENT_W - 12f, y, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }

            cursorY += totalH + 12f
        }

        // ── Grid de KPIs (2 columnas) ─────────────────────────────────────────

        fun drawKpiGrid(kpis: List<Triple<String, String, Int>>) {
            val colW  = CONTENT_W / 2f - 6f
            val cardH = 52f
            ensureSpace(cardH * 2 + 24f)

            kpis.chunked(2).forEach { pair ->
                pair.forEachIndexed { col, (label, value, accentColor) ->
                    val x = MARGIN + col * (colW + 12f)

                    rectPaint.color = COL_BG_SECTION
                    canvas.drawRoundRect(
                        RectF(x, cursorY, x + colW, cursorY + cardH),
                        8f, 8f, rectPaint
                    )

                    // Línea de acento izquierda
                    rectPaint.color = accentColor
                    canvas.drawRoundRect(
                        RectF(x, cursorY, x + 3f, cursorY + cardH),
                        2f, 2f, rectPaint
                    )

                    textPaint.apply {
                        typeface = FONT_NORMAL; textSize = 8f; color = COL_TEXT_SEC
                    }
                    canvas.drawText(label, x + 10f, cursorY + 18f, textPaint)

                    textPaint.apply {
                        typeface = FONT_BOLD; textSize = 15f; color = accentColor
                    }
                    canvas.drawText(value, x + 10f, cursorY + 38f, textPaint)
                }
                cursorY += cardH + 10f
            }
            cursorY += 4f
        }

        // ── Tabla de datos ────────────────────────────────────────────────────

        fun drawTable(headers: List<String>, rows: List<List<String>>) {
            val colW   = CONTENT_W / headers.size
            val rowH   = 18f
            val headerH = 24f

            // Header de tabla
            ensureSpace(headerH + rowH)
            rectPaint.color = COL_ACCENT
            canvas.drawRoundRect(
                RectF(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + headerH),
                6f, 6f, rectPaint
            )
            headers.forEachIndexed { i, h ->
                textPaint.apply {
                    typeface  = FONT_BOLD
                    textSize  = 9f
                    color     = Color.parseColor("#12121F")
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(h, MARGIN + i * colW + colW / 2f, cursorY + 16f, textPaint)
            }
            textPaint.textAlign = Paint.Align.LEFT
            cursorY += headerH

            // Filas de datos
            rows.forEachIndexed { rowIdx, row ->
                ensureSpace(rowH + 2f)

                // Fondo alterno
                bgPaint.color = if (rowIdx % 2 == 0) COL_BG_ROW_A else COL_BG_ROW_B
                canvas.drawRect(
                    MARGIN, cursorY,
                    MARGIN + CONTENT_W, cursorY + rowH,
                    bgPaint
                )

                row.forEachIndexed { colIdx, cell ->
                    val cx = MARGIN + colIdx * colW + colW / 2f
                    textPaint.apply {
                        typeface  = if (colIdx == 0) FONT_BOLD else FONT_NORMAL
                        textSize  = 8.5f
                        color     = when (colIdx) {
                            0    -> COL_TEXT_SEC
                            2    -> COL_ACCENT
                            3    -> COL_ACCENT2
                            else -> COL_TEXT
                        }
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(cell, cx, cursorY + 13f, textPaint)
                }
                textPaint.textAlign = Paint.Align.LEFT
                cursorY += rowH
            }

            // Borde inferior de tabla
            bgPaint.color = COL_ACCENT
            canvas.drawRect(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + 1f, bgPaint)
            cursorY += 14f
        }

        // ── Footer ────────────────────────────────────────────────────────────

        fun drawFooter() {
            val footerY = PAGE_H - 20f
            bgPaint.color = COL_DIVIDER
            canvas.drawRect(MARGIN, footerY - 8f, PAGE_W - MARGIN, footerY - 7.5f, bgPaint)

            textPaint.apply {
                typeface  = FONT_NORMAL
                textSize  = 8f
                color     = COL_TEXT_SEC
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Generado por PhiLab", MARGIN, footerY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                PAGE_W - MARGIN, footerY, textPaint
            )
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private fun openOutputStream(context: Context, fileName: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            stream
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, fileName).outputStream()
        }
    }

    private fun formatDuration(ms: Long): String {
        val s  = ms / 1000; val m = s / 60
        val sc = s % 60;    val cs = (ms % 1000) / 10
        return if (m > 0) "%d:%02d.%02d".format(m, sc, cs)
        else "%d.%02d s".format(sc, cs)
    }
}
