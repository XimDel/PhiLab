package com.example.philab.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.experiment.MotionClassifier
import com.example.philab.domain.pipeline.KinematicPipeline
import com.example.philab.domain.pipeline.PipelineConfig
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exportador de resultados de experimentos a formato PDF.
 *
 * Construye un documento PDF completo a partir de los resultados de un experimento,
 * incluyendo información de sesión, métricas cinemáticas, gráficas y tabla de datos.
 *
 * Las gráficas se generan con [KinematicPipeline] (limpieza y suavizado visual),
 * pero los valores de resumen sobre cada gráfica se toman directamente de
 * [ExperimentResults] para garantizar consistencia con la app y el CSV exportado.
 */
object PdfExporter {

    /**
     * Opciones de configuración para personalizar el contenido del PDF generado.
     */
    data class PdfOptions(
        val includeFecha: Boolean      = true,
        val includeDuracion: Boolean   = true,
        val includeMuestras: Boolean   = true,
        val includeFrecuencia: Boolean = true,
        val includeEscala: Boolean     = true,
        val includeUnidad: Boolean     = true,
        val includeObjeto: Boolean     = false,
        val includeResumen: Boolean    = true,
        val includeTabla: Boolean      = true,
        val includeGraficas: Boolean   = true,
    )

    private const val PAGE_W    = 595f
    private const val PAGE_H    = 842f
    private const val MARGIN    = 36f
    private const val CONTENT_W = PAGE_W - MARGIN * 2f

    private val COL_BG_PAGE    = Color.parseColor("#EAF6F3")
    private val COL_BG_HEADER  = Color.parseColor("#EAF6F3")
    private val COL_BG_SECTION = Color.parseColor("#FFFFFF")
    private val COL_BG_ROW_A   = Color.parseColor("#F4F8F7")
    private val COL_BG_ROW_B   = Color.parseColor("#FFFFFF")
    private val COL_ACCENT     = Color.parseColor("#5FBF9F")
    private val COL_ACCENT2    = Color.parseColor("#6FCF97")
    private val COL_TEXT       = Color.parseColor("#2F3E46")
    private val COL_TEXT_SEC   = Color.parseColor("#5A6269")
    private val COL_DIVIDER    = Color.parseColor("#E0E6E4")

    private val COL_LINE_POS   = Color.parseColor("#1D9E75")
    private val COL_LINE_VEL   = Color.parseColor("#2196F3")
    private val COL_LINE_ACCEL = Color.parseColor("#FF9800")

    private val FONT_BOLD   = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val FONT_NORMAL = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    /**
     * Incertidumbre calculada a partir de los puntos crudos del experimento.
     */
    private data class UncertaintyStats(
        val positionStd: Float,
        val speedStd: Float,
        val accelStd: Float
    )

    /**
     * Calcula la incertidumbre a partir de los puntos crudos.
     * Misma lógica que ExperimentCharts.computeUncertainty().
     */
    private fun computeUncertainty(results: ExperimentResults): UncertaintyStats {
        val pts = results.points
        if (pts.size < 3) return UncertaintyStats(0f, 0f, 0f)

        val distances = mutableListOf<Float>()
        val speeds = mutableListOf<Float>()

        for (i in 1 until pts.size) {
            val dx = pts[i].xCm - pts[i - 1].xCm
            val dy = pts[i].yCm - pts[i - 1].yCm
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val dt = (pts[i].tMs - pts[i - 1].tMs) / 1000f
            distances.add(dist)
            if (dt > 0.005f) speeds.add(dist / dt)
        }

        val posStd = stdDev(distances)
        val velStd = stdDev(speeds)

        val durationS = (pts.last().tMs - pts.first().tMs) / 1000f
        val accelStd = if (durationS > 0f) {
            kotlin.math.sqrt(2f) * velStd / durationS
        } else 0f

        return UncertaintyStats(posStd, velStd, accelStd)
    }

    private fun stdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }

    private fun isDemo(results: ExperimentResults): Boolean =
        results.selectedLabel == "pelota"
                && results.sampleCount == 71
                && results.sampleRateHz == 23f

    /**
     * Genera y guarda un archivo PDF en la carpeta de descargas del dispositivo.
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

    /**
     * Construye el contenido completo del documento PDF.
     *
     * Los valores de resumen sobre cada gráfica (x=, v=, a=) se toman de
     * [ExperimentResults] con incertidumbre calculada por [computeUncertainty],
     * garantizando consistencia con la app.
     */
    private fun buildDocument(
        doc: PdfDocument,
        results: ExperimentResults,
        options: PdfOptions
    ) {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())
        val unit = results.unit
        val renderer = PageRenderer(doc)

        renderer.drawPageBackground()
        renderer.drawAppHeader(dateFormatter.format(Date(results.recordedAt)))

        val metaRows = mutableListOf<Pair<String, String>>()
        if (options.includeObjeto)     metaRows += "Objeto"     to results.selectedLabel
        if (options.includeFecha)      metaRows += "Fecha"      to dateFormatter.format(Date(results.recordedAt))
        if (options.includeDuracion)   metaRows += "Duración"   to formatDuration(results.durationMs)
        if (options.includeMuestras)   metaRows += "Muestras"   to "${results.sampleCount} pts"
        if (options.includeFrecuencia) metaRows += "Frecuencia" to "${"%.1f".format(results.sampleRateHz)} Hz"
        if (options.includeUnidad)     metaRows += "Unidad"     to unit
        if (options.includeEscala && results.isCalibrated)
            metaRows += "Escala" to "${"%.4f".format(results.cmPerPx)} cm/px"
        if (metaRows.isNotEmpty()) {
            renderer.drawSectionTitle("INFORMACIÓN DE LA SESIÓN")
            renderer.drawKeyValueCard(metaRows)
        }

        if (options.includeResumen) {
            renderer.drawSectionTitle("RESULTADOS CINEMÁTICOS")
            renderer.drawKeyValueCard(
                listOf("Tipo de movimiento" to MotionClassifier.classify(results))
            )
            renderer.drawKpiGrid(
                listOf(
                    Triple("Distancia total",   "${"%.2f".format(results.totalDistanceCm)} $unit",  COL_ACCENT),
                    Triple("Desplazamiento",     "${"%.2f".format(results.displacementCm)} $unit",   COL_ACCENT2),
                    Triple("Velocidad media",    "${"%.2f".format(results.avgSpeedCmS)} $unit/s",    COL_ACCENT),
                    Triple("Aceleración media",  "${"%.2f".format(results.avgAccelCmS2)} $unit/s²",  COL_ACCENT2),
                )
            )
        }

        if (options.includeGraficas && !isDemo(results)) {
            val chart = KinematicPipeline.process(
                results = results,
                config  = PipelineConfig(
                    madMultiplier         = 3.5f,
                    windowSize            = 7,
                    velocityMadMultiplier = 4.0f,
                    maxGapToInterpolate   = 0.4f,
                    smoothingWindowSize   = 5,
                    smoothingPasses       = 2,
                    maxChartPoints        = 350
                )
            ).chart

            val uncertainty = computeUncertainty(results)

            renderer.drawSectionTitle("GRÁFICAS")

            renderer.drawChart(
                title     = "Posición vs Tiempo",
                points    = chart.position,
                yLabel    = unit,
                lineColor = COL_LINE_POS,
                summaryLine = "x = ${"%.2f".format(results.totalDistanceCm)} ± ${"%.2f".format(uncertainty.positionStd)} $unit"
            )

            renderer.drawChart(
                title     = "Velocidad vs Tiempo",
                points    = chart.velocity,
                yLabel    = "$unit/s",
                lineColor = COL_LINE_VEL,
                summaryLine = "v = ${"%.2f".format(results.avgSpeedCmS)} ± ${"%.2f".format(uncertainty.speedStd)} $unit/s"
            )

            renderer.drawChart(
                title     = "Aceleración vs Tiempo",
                points    = chart.acceleration,
                yLabel    = "$unit/s²",
                lineColor = COL_LINE_ACCEL,
                summaryLine = "a = ${"%.2f".format(results.avgAccelCmS2)} ± ${"%.2f".format(uncertainty.accelStd)} $unit/s²"
            )
        }

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

        renderer.drawFooter()
        renderer.finishPage()
    }

    /**
     * Clase interna encargada de renderizar el contenido visual del PDF.
     */
    private class PageRenderer(private val doc: PdfDocument) {
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var cursorY: Float = MARGIN

        private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COL_TEXT }
        private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(
                PAGE_W.toInt(), PAGE_H.toInt(), doc.pages.size + 1
            ).create()
            return doc.startPage(info)
        }

        private fun ensureSpace(needed: Float) {
            if (cursorY + needed > PAGE_H - MARGIN - 20f) {
                drawFooter()
                finishPage()
                page   = newPage()
                canvas = page.canvas
                cursorY = MARGIN
                drawPageBackground()
            }
        }

        fun finishPage() { doc.finishPage(page) }

        fun drawPageBackground() {
            bgPaint.color = COL_BG_PAGE
            canvas.drawRect(0f, 0f, PAGE_W, PAGE_H, bgPaint)
        }

        fun drawAppHeader(date: String) {
            bgPaint.color = COL_BG_HEADER
            canvas.drawRect(0f, 0f, PAGE_W, 80f, bgPaint)

            bgPaint.color = COL_ACCENT
            canvas.drawRect(0f, 80f, PAGE_W, 83f, bgPaint)

            textPaint.apply { typeface = FONT_BOLD; textSize = 20f; color = COL_ACCENT }
            canvas.drawText("PhiLab", MARGIN, 34f, textPaint)

            textPaint.apply { typeface = FONT_NORMAL; textSize = 10f; color = COL_TEXT_SEC }
            canvas.drawText("Reporte de experimento", MARGIN, 50f, textPaint)

            textPaint.apply {
                textSize = 10f; color = COL_TEXT
                typeface = FONT_BOLD; textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(date, PAGE_W - MARGIN, 50f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT

            cursorY = 100f
        }

        fun drawSectionTitle(title: String) {
            ensureSpace(28f)
            textPaint.apply { typeface = FONT_BOLD; textSize = 9f; color = COL_ACCENT }
            canvas.drawText(title, MARGIN, cursorY + 12f, textPaint)
            bgPaint.color = COL_ACCENT
            canvas.drawRect(MARGIN, cursorY + 16f, MARGIN + 32f, cursorY + 17.5f, bgPaint)
            cursorY += 26f
        }

        fun drawKeyValueCard(rows: List<Pair<String, String>>) {
            val rowH   = 22f
            val totalH = rows.size * rowH + 8f
            ensureSpace(totalH + 8f)

            rectPaint.color = COL_BG_SECTION
            canvas.drawRoundRect(
                RectF(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + totalH),
                8f, 8f, rectPaint
            )

            rows.forEachIndexed { i, (key, value) ->
                val y = cursorY + 8f + (i + 1) * rowH - 6f

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
                    typeface = FONT_BOLD; color = COL_TEXT; textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(value, MARGIN + CONTENT_W - 12f, y, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
            cursorY += totalH + 12f
        }

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
                    rectPaint.color = accentColor
                    canvas.drawRoundRect(
                        RectF(x, cursorY, x + 3f, cursorY + cardH),
                        2f, 2f, rectPaint
                    )
                    textPaint.apply { typeface = FONT_NORMAL; textSize = 8f; color = COL_TEXT_SEC }
                    canvas.drawText(label, x + 10f, cursorY + 18f, textPaint)
                    textPaint.apply { typeface = FONT_BOLD; textSize = 15f; color = accentColor }
                    canvas.drawText(value, x + 10f, cursorY + 38f, textPaint)
                }
                cursorY += cardH + 10f
            }
            cursorY += 4f
        }

        /**
         * Dibuja una gráfica con una línea de resumen tomada de [ExperimentResults].
         *
         * @param title Título de la gráfica.
         * @param points Datos de la serie procesados por el pipeline (solo para dibujar).
         * @param yLabel Unidad del eje Y.
         * @param lineColor Color de la línea.
         * @param summaryLine Texto de resumen (e.g. "v = 4.16 ± 2.31 cm/s") tomado
         *   de ExperimentResults, no del pipeline.
         */
        fun drawChart(
            title: String,
            points: List<Pair<Float, Float>>,
            yLabel: String,
            lineColor: Int,
            summaryLine: String? = null
        ) {
            val chartH = 120f
            val summaryH = if (summaryLine != null) 20f else 0f
            ensureSpace(chartH + 50f + summaryH)

            textPaint.apply { typeface = FONT_BOLD; textSize = 8f; color = COL_TEXT_SEC }
            canvas.drawText(title, MARGIN, cursorY + 10f, textPaint)
            cursorY += 16f

            if (points.size < 2) {
                cursorY += chartH + 14f + summaryH
                return
            }

            val chartTop    = cursorY
            val chartBottom = cursorY + chartH
            val chartLeft   = MARGIN + 32f
            val chartRight  = MARGIN + CONTENT_W

            rectPaint.color = COL_BG_SECTION
            canvas.drawRoundRect(
                RectF(MARGIN, chartTop, MARGIN + CONTENT_W, chartBottom + 16f),
                6f, 6f, rectPaint
            )

            val minT   = points.first().first
            val maxT   = points.last().first
            val minY   = points.minOf { it.second }
            val maxY   = points.maxOf { it.second }
            val rangeT = (maxT - minT).coerceAtLeast(0.001f)
            val rangeY = (maxY - minY).coerceAtLeast(0.001f)
            val padY   = rangeY * 0.15f
            val yLow   = minY - padY
            val yHigh  = maxY + padY

            fun mapX(t: Float) = chartLeft + (t - minT) / rangeT * (chartRight - chartLeft)
            fun mapY(v: Float) = chartBottom - (v - yLow) / (yHigh - yLow) * chartH

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EAEAEA"); strokeWidth = 0.5f
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_TEXT_SEC; textSize = 6.5f
                typeface = FONT_NORMAL; textAlign = Paint.Align.RIGHT
            }
            for (i in 0..4) {
                val v = yLow + (yHigh - yLow) * i / 4f
                val y = mapY(v)
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
                canvas.drawText("%.2f".format(v), chartLeft - 3f, y + 2f, labelPaint)
            }

            val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_DIVIDER; strokeWidth = 1f
            }
            canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
            canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

            val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_TEXT_SEC; textSize = 6.5f
                typeface = FONT_NORMAL; textAlign = Paint.Align.CENTER
            }
            listOf(0, points.size / 2, points.size - 1).forEach { idx ->
                val (t, _) = points[idx]
                canvas.drawText("%.1fs".format(t), mapX(t), chartBottom + 10f, xLabelPaint)
            }

            canvas.save()
            canvas.rotate(-90f, MARGIN + 8f, chartTop + chartH / 2f)
            textPaint.apply {
                typeface = FONT_NORMAL; textSize = 6f
                color = COL_TEXT_SEC; textAlign = Paint.Align.CENTER
            }
            canvas.drawText(yLabel, MARGIN + 8f, chartTop + chartH / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.restore()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor and 0x00FFFFFF or 0x22000000
                style = Paint.Style.FILL
            }
            val fillPath = Path()
            val baseline = mapY(yLow.coerceAtLeast(0f))
            points.forEachIndexed { i, (t, v) ->
                val x = mapX(t); val y = mapY(v)
                if (i == 0) fillPath.moveTo(x, baseline)
                fillPath.lineTo(x, y)
            }
            fillPath.lineTo(mapX(points.last().first), baseline)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor; strokeWidth = 1.8f
                style = Paint.Style.STROKE
            }
            val linePath = Path()
            points.forEachIndexed { i, (t, v) ->
                val x = mapX(t); val y = mapY(v)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            canvas.drawPath(linePath, linePaint)

            cursorY += chartH + 22f

            if (summaryLine != null) {
                val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COL_DIVIDER; strokeWidth = 0.5f
                }
                canvas.drawLine(MARGIN + 4f, cursorY, MARGIN + CONTENT_W - 4f, cursorY, divPaint)
                cursorY += 6f

                textPaint.apply {
                    typeface  = FONT_BOLD
                    textSize  = 8.5f
                    color     = lineColor
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(summaryLine, MARGIN + 8f, cursorY + 9f, textPaint)
                cursorY += 18f
            }
        }

        fun drawTable(headers: List<String>, rows: List<List<String>>) {
            val colW    = CONTENT_W / headers.size
            val rowH    = 18f
            val headerH = 24f

            ensureSpace(headerH + rowH)
            rectPaint.color = COL_ACCENT
            canvas.drawRoundRect(
                RectF(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + headerH),
                6f, 6f, rectPaint
            )
            headers.forEachIndexed { i, h ->
                textPaint.apply {
                    typeface = FONT_BOLD; textSize = 9f
                    color = COL_TEXT; textAlign = Paint.Align.CENTER
                }
                canvas.drawText(h, MARGIN + i * colW + colW / 2f, cursorY + 16f, textPaint)
            }
            textPaint.textAlign = Paint.Align.LEFT
            cursorY += headerH

            rows.forEachIndexed { rowIdx, row ->
                ensureSpace(rowH + 2f)
                bgPaint.color = if (rowIdx % 2 == 0) COL_BG_ROW_A else COL_BG_ROW_B
                canvas.drawRect(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + rowH, bgPaint)

                row.forEachIndexed { colIdx, cell ->
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
                    canvas.drawText(
                        cell,
                        MARGIN + colIdx * colW + colW / 2f,
                        cursorY + 13f,
                        textPaint
                    )
                }
                textPaint.textAlign = Paint.Align.LEFT
                cursorY += rowH
            }

            bgPaint.color = COL_ACCENT
            canvas.drawRect(MARGIN, cursorY, MARGIN + CONTENT_W, cursorY + 1f, bgPaint)
            cursorY += 14f
        }

        fun drawFooter() {
            val footerY = PAGE_H - 20f
            bgPaint.color = COL_DIVIDER
            canvas.drawRect(MARGIN, footerY - 8f, PAGE_W - MARGIN, footerY - 7.5f, bgPaint)

            textPaint.apply {
                typeface = FONT_NORMAL; textSize = 8f
                color = COL_TEXT_SEC; textAlign = Paint.Align.LEFT
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
        val s = ms / 1000; val m = s / 60
        val sc = s % 60;   val cs = (ms % 1000) / 10
        return if (m > 0) "%d:%02d.%02d".format(m, sc, cs)
        else "%d.%02d s".format(sc, cs)
    }
}