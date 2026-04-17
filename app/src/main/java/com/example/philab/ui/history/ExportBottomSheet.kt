package com.example.philab.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.domain.export.CsvExporter
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SheetBg          = Color(0xFFF8F8FC)
private val CardBg           = Color.White
private val AccentGreen      = Color(0xFF1D9E75)
private val TextPrimary      = Color(0xFF1A1A2E)
private val TextSecondary    = Color(0xFF7A7A8C)
private val DividerCol       = Color(0xFFEEEEF2)
private val ToggleSelected   = Color(0xFF22BE8B)
private val ToggleUnselected = Color(0xFFB7B3B3)

/**
 * Opciones de exportación CSV seleccionables por el usuario en la UI.
 *
 * Cada propiedad controla si la sección o campo correspondiente se incluye
 * en el archivo generado. Los valores predeterminados representan la
 * configuración recomendada para una exportación estándar.
 *
 * @property fecha      Incluir fecha y hora de la sesión en la metadata.
 * @property duracion   Incluir duración total de la sesión.
 * @property muestras   Incluir número total de muestras capturadas.
 * @property frecuencia Incluir frecuencia de muestreo en Hz.
 * @property escala     Incluir el factor de escala `cm/px` de la calibración.
 * @property unidad     Incluir la unidad de medida utilizada.
 * @property objeto     Incluir la etiqueta del objeto seguido.
 * @property resumen    Incluir el resumen cinemático calculado.
 * @property tabla      Incluir la tabla de puntos de trayectoria.
 */
private data class CsvUiOptions(
    val fecha: Boolean      = true,
    val duracion: Boolean   = true,
    val muestras: Boolean   = true,
    val frecuencia: Boolean = true,
    val escala: Boolean     = true,
    val unidad: Boolean     = false,
    val objeto: Boolean     = false,
    val resumen: Boolean    = true,
    val tabla: Boolean      = true,
)

/**
 * Convierte las opciones de UI [CsvUiOptions] al modelo de dominio [CsvExporter.CsvOptions].
 *
 * La bandera `includeMetadata` se activa si al menos uno de los campos de
 * metadata está seleccionado.
 */
private fun CsvUiOptions.toCsvOptions() = CsvExporter.CsvOptions(
    includeMetadata   = fecha || duracion || muestras || frecuencia || escala || unidad || objeto,
    includeFecha      = fecha,
    includeDuracion   = duracion,
    includeMuestras   = muestras,
    includeFrecuencia = frecuencia,
    includeEscala     = escala,
    includeUnidad     = unidad,
    includeObjeto     = objeto,
    includeResumen    = resumen,
    includeTabla      = tabla,
)

/**
 * Opciones de exportación PDF seleccionables por el usuario en la UI.
 *
 * Extiende las opciones CSV con la posibilidad de incluir gráficas en el
 * documento generado. Los valores predeterminados representan la configuración
 * recomendada para un informe completo.
 *
 * @property fecha      Incluir fecha y hora de la sesión en la metadata.
 * @property duracion   Incluir duración total de la sesión.
 * @property muestras   Incluir número total de muestras capturadas.
 * @property frecuencia Incluir frecuencia de muestreo en Hz.
 * @property escala     Incluir el factor de escala `cm/px` de la calibración.
 * @property unidad     Incluir la unidad de medida utilizada.
 * @property objeto     Incluir la etiqueta del objeto seguido.
 * @property resumen    Incluir el resumen cinemático calculado.
 * @property tabla      Incluir la tabla de puntos de trayectoria.
 * @property graficas   Incluir las gráficas cinemáticas generadas.
 */
internal data class PdfUiOptions(
    val fecha: Boolean      = true,
    val duracion: Boolean   = true,
    val muestras: Boolean   = true,
    val frecuencia: Boolean = true,
    val escala: Boolean     = true,
    val unidad: Boolean     = false,
    val objeto: Boolean     = false,
    val resumen: Boolean    = true,
    val tabla: Boolean      = true,
    val graficas: Boolean   = true,
)

/**
 * Convierte las opciones de UI [PdfUiOptions] al modelo de dominio [PdfExporter.PdfOptions].
 */
private fun PdfUiOptions.toPdfOptions() = PdfExporter.PdfOptions(
    includeFecha      = fecha,
    includeDuracion   = duracion,
    includeMuestras   = muestras,
    includeFrecuencia = frecuencia,
    includeEscala     = escala,
    includeUnidad     = unidad,
    includeObjeto     = objeto,
    includeResumen    = resumen,
    includeTabla      = tabla,
    includeGraficas   = graficas,
)

/**
 * Bottom sheet modal para configurar y lanzar la exportación de resultados.
 *
 * Presenta dos pestañas —CSV y PDF— con opciones de configuración independientes.
 * La exportación se ejecuta en el dispatcher IO y muestra un Toast al completarse.
 * Si la exportación es exitosa, el sheet se cierra automáticamente.
 *
 * El modo "demo" deshabilita la opción de gráficas en PDF cuando los resultados
 * corresponden al experimento de demostración predeterminado.
 *
 * @param results      Resultados del experimento a exportar.
 * @param sheetState   Estado del [ModalBottomSheet] gestionado por el caller.
 * @param onDismiss    Callback invocado al cerrar el sheet manualmente o tras exportar.
 * @param onCsvSaved   Callback invocado con `(éxito, nombreArchivo)` al terminar la exportación CSV.
 * @param onPdfSaved   Callback invocado con `(éxito, nombreArchivo)` al terminar la exportación PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    results: ExperimentResults,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCsvSaved: (success: Boolean, fileName: String) -> Unit,
    onPdfSaved: (success: Boolean, fileName: String) -> Unit = { _, _ -> },
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var csvOptions  by remember { mutableStateOf(CsvUiOptions()) }
    var pdfOptions  by remember { mutableStateOf(PdfUiOptions()) }

    val isDemo = results.selectedLabel == "pelota"
            && results.sampleCount == 71
            && results.sampleRateHz == 23f

    fun exportCsv() {
        if (isExporting) return
        scope.launch {
            isExporting = true
            val success = withContext(Dispatchers.IO) {
                CsvExporter.saveToDowloads(
                    context = context,
                    results = results,
                    options = csvOptions.toCsvOptions()
                )
            }
            isExporting = false
            val fileName = CsvExporter.buildPublicFileName(results)
            if (success) {
                Toast.makeText(context, "CSV guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al guardar el CSV", Toast.LENGTH_SHORT).show()
            }
            onCsvSaved(success, fileName)
            if (success) onDismiss()
        }
    }

    fun exportPdf() {
        if (isExporting) return
        scope.launch {
            isExporting = true
            val fileName = PdfExporter.buildFileName(results)
            val success = withContext(Dispatchers.IO) {
                PdfExporter.saveToDownloads(
                    context  = context,
                    results  = results,
                    options  = pdfOptions.toPdfOptions(),
                    fileName = fileName
                )
            }
            isExporting = false
            if (success) {
                Toast.makeText(context, "PDF guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al guardar el PDF", Toast.LENGTH_SHORT).show()
            }
            onPdfSaved(success, fileName)
            if (success) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SheetBg,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFCCCCDD))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text       = "Exportar resultados",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            ExportTabRow(
                selectedTab = selectedTab,
                onSelect    = { selectedTab = it }
            )

            Spacer(Modifier.height(20.dp))

            when (selectedTab) {
                0 -> CsvTabContent(
                    results     = results,
                    options     = csvOptions,
                    onChange    = { csvOptions = it },
                    isExporting = isExporting,
                    onExport    = { exportCsv() }
                )
                1 -> PdfTabContent(
                    results          = results,
                    options          = pdfOptions,
                    onChange         = { pdfOptions = it },
                    isExporting      = isExporting,
                    graficasDisabled = isDemo,
                    onExport         = { exportPdf() }
                )
            }
        }
    }
}

/**
 * Fila de pestañas con dos opciones: CSV y PDF.
 *
 * Renderiza dos botones estilo píldora dentro de un contenedor redondeado.
 * La pestaña activa se resalta con el color de acento; la inactiva es transparente.
 *
 * @param selectedTab Índice de la pestaña activa (`0` = CSV, `1` = PDF).
 * @param onSelect    Callback invocado con el índice de la pestaña pulsada.
 */
@Composable
private fun ExportTabRow(selectedTab: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEEEEF5))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple(0, "CSV", Icons.Filled.TableChart),
            Triple(1, "PDF", Icons.Filled.Description)
        ).forEach { (idx, label, icon) ->
            val selected = selectedTab == idx
            val color    = AccentGreen
            Button(
                onClick  = { onSelect(idx) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape    = RoundedCornerShape(9.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (selected) color else Color.Transparent,
                    contentColor   = if (selected) Color.White else TextSecondary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (selected) 2.dp else 0.dp
                )
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Contenido de la pestaña de exportación CSV.
 *
 * Muestra dos secciones de opciones con [ToggleRow]: metadata de la sesión
 * y resultados. Incluye un contador de filas a exportar cuando la tabla está
 * activada, y un botón de exportación que se deshabilita si no hay ninguna
 * opción seleccionada o si hay una exportación en curso.
 *
 * @param results     Resultados del experimento; se usa para mostrar el número de filas
 *                    y para condicionar la opción de escala según la calibración.
 * @param options     Estado actual de las opciones CSV.
 * @param onChange    Callback invocado con las opciones actualizadas al cambiar un toggle.
 * @param isExporting Indica si hay una exportación en curso; deshabilita el botón.
 * @param onExport    Callback invocado al pulsar el botón de exportación.
 */
@Composable
private fun CsvTabContent(
    results: ExperimentResults,
    options: CsvUiOptions,
    onChange: (CsvUiOptions) -> Unit,
    isExporting: Boolean = false,
    onExport: () -> Unit,
) {
    ToggleSection(title = "Metadata de la sesión") {
        ToggleRow("Fecha y hora",       options.fecha)      { onChange(options.copy(fecha = it)) }
        ToggleRow("Duración",           options.duracion)   { onChange(options.copy(duracion = it)) }
        ToggleRow("Muestras",           options.muestras)   { onChange(options.copy(muestras = it)) }
        ToggleRow("Frecuencia",         options.frecuencia) { onChange(options.copy(frecuencia = it)) }
        if (results.isCalibrated)
            ToggleRow("Escala (cm/px)", options.escala)     { onChange(options.copy(escala = it)) }
        ToggleRow("Unidad",             options.unidad)     { onChange(options.copy(unidad = it)) }
        ToggleRow("Objeto seguido",     options.objeto)     { onChange(options.copy(objeto = it)) }
    }

    Spacer(Modifier.height(12.dp))

    ToggleSection(title = "Resultados") {
        ToggleRow("Resumen cinemático", options.resumen) { onChange(options.copy(resumen = it)) }
        ToggleRow("Tabla de datos",     options.tabla)   { onChange(options.copy(tabla = it)) }
    }

    Spacer(Modifier.height(8.dp))

    val totalRows = if (options.tabla) results.points.size else 0
    if (totalRows > 0) {
        Text(
            text     = "Se exportarán $totalRows filas de datos",
            color    = AccentGreen,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick  = onExport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        enabled  = !isExporting && (options.resumen || options.tabla ||
                options.fecha || options.duracion || options.muestras ||
                options.frecuencia || options.escala || options.unidad || options.objeto)
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                color       = Color.White,
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Guardando…", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        } else {
            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Exportar CSV", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }
    }
}

/**
 * Contenido de la pestaña de exportación PDF.
 *
 * Idéntica en estructura a [CsvTabContent] pero añade la opción de gráficas.
 * Cuando [graficasDisabled] es `true`, el toggle de gráficas se deshabilita y
 * se muestra un texto indicando que el experimento es de demostración.
 *
 * @param results          Resultados del experimento.
 * @param options          Estado actual de las opciones PDF.
 * @param onChange         Callback invocado con las opciones actualizadas al cambiar un toggle.
 * @param isExporting      Indica si hay una exportación en curso; deshabilita el botón.
 * @param graficasDisabled Si `true`, la opción de gráficas se fuerza a deshabilitada.
 * @param onExport         Callback invocado al pulsar el botón de exportación.
 */
@Composable
private fun PdfTabContent(
    results: ExperimentResults,
    options: PdfUiOptions,
    onChange: (PdfUiOptions) -> Unit,
    isExporting: Boolean = false,
    graficasDisabled: Boolean = false,
    onExport: () -> Unit,
) {
    ToggleSection(title = "Metadata de la sesión") {
        ToggleRow("Fecha y hora",       options.fecha)      { onChange(options.copy(fecha = it)) }
        ToggleRow("Duración",           options.duracion)   { onChange(options.copy(duracion = it)) }
        ToggleRow("Muestras",           options.muestras)   { onChange(options.copy(muestras = it)) }
        ToggleRow("Frecuencia",         options.frecuencia) { onChange(options.copy(frecuencia = it)) }
        if (results.isCalibrated)
            ToggleRow("Escala (cm/px)", options.escala)     { onChange(options.copy(escala = it)) }
        ToggleRow("Unidad",             options.unidad)     { onChange(options.copy(unidad = it)) }
        ToggleRow("Objeto seguido",     options.objeto)     { onChange(options.copy(objeto = it)) }
    }

    Spacer(Modifier.height(12.dp))

    ToggleSection(title = "Resultados") {
        ToggleRow("Resumen cinemático", options.resumen) { onChange(options.copy(resumen = it)) }
        ToggleRow("Tabla de datos",     options.tabla)   { onChange(options.copy(tabla = it)) }
        ToggleRow(
            label    = "Gráficas",
            checked  = options.graficas && !graficasDisabled,
            enabled  = !graficasDisabled,
            onToggle = { onChange(options.copy(graficas = it)) }
        )
        if (graficasDisabled) {
            Text(
                text     = "Experimento de demostración",
                fontSize = 11.sp,
                color    = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
    }

    val totalRows = if (options.tabla) results.points.size else 0
    if (totalRows > 0) {
        Text(
            text     = "Se exportarán $totalRows filas de datos",
            color    = AccentGreen,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick  = onExport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        enabled  = !isExporting && (options.resumen || options.tabla ||
                options.fecha || options.duracion || options.muestras ||
                options.frecuencia || options.escala || options.unidad || options.objeto)
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                color       = Color.White,
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Generando…", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        } else {
            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Exportar PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }
    }
}

/**
 * Contenedor con tarjeta redondeada que agrupa un conjunto de [ToggleRow] bajo
 * un título de sección.
 *
 * @param title   Texto del encabezado de la sección.
 * @param content Contenido composable de la sección; típicamente una serie de [ToggleRow].
 */
@Composable
private fun ToggleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
    ) {
        Text(
            text       = title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = TextSecondary,
            modifier   = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        HorizontalDivider(color = DividerCol, thickness = 0.5.dp)
        Column(content = content)
    }
}

/**
 * Fila con un texto descriptivo y un [Switch] para activar o desactivar una opción
 * de exportación.
 *
 * @param label    Texto descriptivo de la opción.
 * @param checked  Estado actual del switch.
 * @param enabled  Si `false`, el switch se muestra deshabilitado y el texto en color secundario.
 * @param onToggle Callback invocado con el nuevo valor booleano al cambiar el switch.
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text     = label,
            fontSize = 13.sp,
            color    = if (enabled) TextPrimary else TextSecondary,
        )
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            enabled         = enabled,
            colors          = SwitchDefaults.colors(
                checkedThumbColor           = Color.White,
                checkedTrackColor           = ToggleSelected,
                uncheckedThumbColor         = Color.White,
                uncheckedTrackColor         = ToggleUnselected,
                disabledCheckedTrackColor   = ToggleUnselected,
                disabledUncheckedTrackColor = ToggleUnselected,
            )
        )
    }
}