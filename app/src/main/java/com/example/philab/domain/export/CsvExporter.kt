package com.example.philab.domain.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.experiment.MotionClassifier
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilidad para exportar resultados de experimentos a formato CSV.
 *
 * Permite generar el contenido del archivo y guardarlo en el dispositivo,
 * incluyendo diferentes secciones configurables como metadatos, resumen
 * cinemático y tabla de datos.
 */
object CsvExporter {

    /**
     * Opciones de configuración para controlar qué secciones del CSV se incluyen.
     *
     * @param includeMetadata Indica si se incluye el bloque de metadatos.
     * @param includeFecha Indica si se incluye la fecha de la sesión.
     * @param includeDuracion Indica si se incluye la duración del experimento.
     * @param includeMuestras Indica si se incluye el número de muestras.
     * @param includeFrecuencia Indica si se incluye la frecuencia de muestreo.
     * @param includeEscala Indica si se incluye la escala de conversión.
     * @param includeUnidad Indica si se incluye la unidad de medida.
     * @param includeObjeto Indica si se incluye el objeto analizado.
     * @param includeResumen Indica si se incluye el resumen cinemático.
     * @param includeTabla Indica si se incluye la tabla de datos.
     */
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

    /**
     * Genera el contenido del archivo CSV a partir de los resultados.
     *
     * @param results Resultados del experimento.
     * @param options Configuración de secciones a incluir.
     * @return Cadena de texto en formato CSV.
     */
    fun buildCsv(results: ExperimentResults, options: CsvOptions): String {
        val sb = StringBuilder()
        val unit = results.unit
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

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

        if (options.includeResumen) {
            sb.appendLine("# RESULTADOS CINEMÁTICOS")
            sb.appendLine("Tipo de movimiento,${MotionClassifier.classify(results)}")
            sb.appendLine("Distancia total ($unit),${"%.4f".format(results.totalDistanceCm)}")
            sb.appendLine("Desplazamiento ($unit),${"%.4f".format(results.displacementCm)}")
            sb.appendLine("Velocidad media ($unit/s),${"%.4f".format(results.avgSpeedCmS)}")
            sb.appendLine("Aceleración media ($unit/s²),${"%.4f".format(results.avgAccelCmS2)}")
            sb.appendLine()
        }

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
     * Guarda el archivo CSV en la carpeta de descargas del dispositivo.
     *
     * @param context Contexto de la aplicación.
     * @param results Resultados del experimento.
     * @param options Configuración de exportación.
     * @param fileName Nombre del archivo a generar.
     * @return true si la operación fue exitosa, false en caso contrario.
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

    /**
     * Abre un flujo de salida para escribir el archivo CSV según la versión de Android.
     */
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

    /**
     * Genera un nombre de archivo público basado en el contenido de la sesión.
     *
     * @param results Resultados del experimento.
     * @return Nombre del archivo CSV.
     */
    internal fun buildPublicFileName(results: ExperimentResults): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(results.recordedAt))
        val label = results.selectedLabel
            .replace(" ", "_")
            .replace("[^a-zA-Z0-9_]".toRegex(), "")
            .take(20)
        return "PhiLab_${label}_$ts.csv"
    }

    /**
     * Formatea una duración en milisegundos a una representación legible.
     *
     * @param ms Duración en milisegundos.
     * @return Cadena formateada.
     */
    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        val cs = (ms % 1000) / 10
        return if (m > 0) "%d:%02d.%02d".format(m, sec, cs)
        else "%d.%02d s".format(sec, cs)
    }
}