package com.example.philab.domain.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.philab.domain.experiment.ExperimentResults
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta ExperimentResults a CSV.
 *
 * Toggles que controlan qué secciones se incluyen:
 *  - includeMetadata  → bloque de metadatos de sesión al inicio
 *  - includeResumen   → bloque de resultados cinemáticos
 *  - includeTabla     → filas de datos t, x, y
 *
 * El archivo se guarda en Downloads/ con MediaStore (API 29+)
 * o directamente en el directorio Downloads (API < 29).
 */
object CsvExporter {

    data class CsvOptions(
        val includeMetadata: Boolean = true,
        val includeFecha: Boolean    = true,
        val includeDuracion: Boolean = true,
        val includeMuestras: Boolean = true,
        val includeFrecuencia: Boolean = true,
        val includeEscala: Boolean   = true,
        val includeUnidad: Boolean   = true,
        val includeObjeto: Boolean   = true,
        val includeResumen: Boolean  = true,
        val includeTabla: Boolean    = true,
    )

    /** Genera el contenido CSV como String. */
    fun buildCsv(results: ExperimentResults, options: CsvOptions): String {
        val sb = StringBuilder()
        val unit = results.unit
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        // ── Metadata ──────────────────────────────────────────────────────────
        if (options.includeMetadata) {
            sb.appendLine("# METADATA DE LA SESIÓN")
            if (options.includeObjeto)
                sb.appendLine("Objeto,${results.selectedLabel}")
            if (options.includeFecha)
                sb.appendLine("Fecha,${dateFormatter.format(Date(results.recordedAt))}")
            if (options.includeDuracion)
                sb.appendLine("Duración,${formatDuration(results.durationMs)}")
            if (options.includeMuestras)
                sb.appendLine("Muestras,${results.sampleCount}")
            if (options.includeFrecuencia)
                sb.appendLine("Frecuencia (Hz),${"%.1f".format(results.sampleRateHz)}")
            if (options.includeUnidad)
                sb.appendLine("Unidad,$unit")
            if (options.includeEscala && results.isCalibrated)
                sb.appendLine("Escala (cm/px),${"%.4f".format(results.cmPerPx)}")
            sb.appendLine()
        }

        // ── Resumen cinemático ────────────────────────────────────────────────
        if (options.includeResumen) {
            sb.appendLine("# RESULTADOS CINEMÁTICOS")
            sb.appendLine("Distancia total ($unit),${"%.4f".format(results.totalDistanceCm)}")
            sb.appendLine("Desplazamiento ($unit),${"%.4f".format(results.displacementCm)}")
            sb.appendLine("Velocidad media ($unit/s),${"%.4f".format(results.avgSpeedCmS)}")
            sb.appendLine("Aceleración media ($unit/s²),${"%.4f".format(results.avgAccelCmS2)}")
            sb.appendLine()
        }

        // ── Tabla de datos ────────────────────────────────────────────────────
        if (options.includeTabla) {
            sb.appendLine("# DATOS CAPTURADOS")
            sb.appendLine("t (s),x ($unit),y ($unit)")
            results.points.forEach { pt ->
                sb.appendLine(
                    "${"%.4f".format(pt.tSeconds)}," +
                            "${"%.4f".format(pt.xCm)}," +
                            "${"%.4f".format(pt.yCm)}"
                )
            }
        }

        return sb.toString()
    }

    /**
     * Guarda el CSV en la carpeta Downloads del dispositivo.
     * Devuelve true si tuvo éxito.
     */
    fun saveToDowloads(
        context: Context,
        results: ExperimentResults,
        options: CsvOptions,
        fileName: String = buildPublicFileName(results)
    ): Boolean {
        return try {
            val csv = buildCsv(results, options)
            val stream = openOutputStream(context, fileName) ?: return false
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            stream.use { it.write(bom + csv.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun openOutputStream(context: Context, fileName: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null
            // Marcar como listo
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

    internal fun buildPublicFileName(results: ExperimentResults): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(results.recordedAt))
        val label = results.selectedLabel
            .replace(" ", "_")
            .replace("[^a-zA-Z0-9_]".toRegex(), "")
            .take(20)
        return "PhiLab_${label}_$ts.csv"
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        val cs = (ms % 1000) / 10
        return if (m > 0) "%d:%02d.%02d".format(m, sec, cs)
        else "%d.%02d s".format(sec, cs)
    }
}