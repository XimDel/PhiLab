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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.domain.export.CsvExporter
import com.example.philab.domain.experiment.ExperimentResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Paleta ───────────────────────────────────────────────────────────────────

private val SheetBg       = Color(0xFFF8F8FC)
private val CardBg        = Color.White
private val AccentGreen   = Color(0xFF1D9E75)
private val AccentOrange  = Color(0xFFFF9800)
private val TextPrimary   = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF7A7A8C)
private val DividerCol    = Color(0xFFEEEEF2)
private val TabSelected   = Color(0xFF1D9E75)
private val TabUnselected = Color(0xFFCCCCDD)

// ─── Modelo de opciones UI ────────────────────────────────────────────────────

private data class CsvUiOptions(
    val fecha: Boolean        = true,
    val duracion: Boolean     = true,
    val muestras: Boolean     = false,
    val frecuencia: Boolean   = false,
    val escala: Boolean       = false,
    val unidad: Boolean       = false,
    val objeto: Boolean       = false,
    val resumen: Boolean      = true,
    val tabla: Boolean        = true,
)

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

internal data class PdfUiOptions(
    val fecha: Boolean      = true,
    val duracion: Boolean   = true,
    val muestras: Boolean   = false,
    val frecuencia: Boolean = false,
    val escala: Boolean     = false,
    val unidad: Boolean     = false,
    val objeto: Boolean     = false,
    val resumen: Boolean    = true,
    val tabla: Boolean      = true,
    val graficas: Boolean   = false,  // reservado para cuando Vico esté integrado
)

// ─── BottomSheet principal ────────────────────────────────────────────────────

/**
 * BottomSheet de exportación con tabs PDF / CSV y toggles individuales.
 *
 * Uso en ResultsScreen:
 *
 *   val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 *   var showSheet by remember { mutableStateOf(false) }
 *
 *   if (showSheet) {
 *       ExportBottomSheet(
 *           results    = results,
 *           sheetState = sheetState,
 *           onDismiss  = { showSheet = false },
 *           onCsvSaved = { success -> ... },
 *           onPdfExport = { options -> ... },  // implementar luego
 *       )
 *   }
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    results: ExperimentResults,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCsvSaved: (success: Boolean, fileName: String) -> Unit,
    onPdfExport: () -> Unit,
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var csvOptions  by remember { mutableStateOf(CsvUiOptions()) }
    var pdfOptions  by remember { mutableStateOf(PdfUiOptions()) }

    fun exportCsv() {
        if (isExporting) return
        scope.launch {
            isExporting = true
            val success = withContext(Dispatchers.IO) {
                CsvExporter.saveToDowloads(
                    context  = context,
                    results  = results,
                    options  = csvOptions.toCsvOptions()
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

            // ── Título ──
            Text(
                text       = "Exportar resultados",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            // ── Tabs CSV / PDF ──
            ExportTabRow(
                selectedTab = selectedTab,
                onSelect    = { selectedTab = it }
            )

            Spacer(Modifier.height(20.dp))

            // ── Contenido del tab ──
            when (selectedTab) {
                0 -> CsvTabContent(
                    results     = results,
                    options     = csvOptions,
                    onChange    = { csvOptions = it },
                    isExporting = isExporting,
                    onExport    = { exportCsv() }
                )
                1 -> PdfTabContent(
                    results  = results,
                    options  = pdfOptions,
                    onChange = { pdfOptions = it },
                    onExport = { onPdfExport() }
                )
            }
        }
    }
}

// ─── Tab row ─────────────────────────────────────────────────────────────────

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
            val color    = if (idx == 0) AccentGreen else AccentOrange
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

// ─── Tab CSV ──────────────────────────────────────────────────────────────────

@Composable
private fun CsvTabContent(
    results: ExperimentResults,
    options: CsvUiOptions,
    onChange: (CsvUiOptions) -> Unit,
    isExporting: Boolean = false,
    onExport: () -> Unit,
) {
    ToggleSection(title = "Metadata de la sesión") {
        ToggleRow("Fecha y hora",       options.fecha)        { onChange(options.copy(fecha = it)) }
        ToggleRow("Duración",           options.duracion)     { onChange(options.copy(duracion = it)) }
        ToggleRow("Muestras",           options.muestras)     { onChange(options.copy(muestras = it)) }
        ToggleRow("Frecuencia",         options.frecuencia)   { onChange(options.copy(frecuencia = it)) }
        if (results.isCalibrated)
            ToggleRow("Escala (cm/px)", options.escala)       { onChange(options.copy(escala = it)) }
        ToggleRow("Unidad",             options.unidad)       { onChange(options.copy(unidad = it)) }
        ToggleRow("Objeto seguido",     options.objeto)       { onChange(options.copy(objeto = it)) }
    }

    Spacer(Modifier.height(12.dp))

    ToggleSection(title = "Resultados") {
        ToggleRow("Resumen cinemático", options.resumen) { onChange(options.copy(resumen = it)) }
        ToggleRow("Tabla de datos",     options.tabla)   { onChange(options.copy(tabla = it)) }
    }

    Spacer(Modifier.height(8.dp))

    // Preview de filas
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

// ─── Tab PDF ──────────────────────────────────────────────────────────────────

@Composable
private fun PdfTabContent(
    results: ExperimentResults,
    options: PdfUiOptions,
    onChange: (PdfUiOptions) -> Unit,
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
            label   = "Gráficas  (próximamente)",
            checked = false,
            enabled = false,
            onToggle = {}
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick  = onExport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = AccentOrange),
        enabled  = options.resumen || options.tabla ||
                options.fecha || options.duracion || options.muestras ||
                options.frecuencia || options.escala || options.unidad || options.objeto
    ) {
        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Exportar PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
    }
}

// ─── Componentes internos ─────────────────────────────────────────────────────

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
                checkedThumbColor       = Color.White,
                checkedTrackColor       = AccentGreen,
                uncheckedThumbColor     = Color.White,
                uncheckedTrackColor     = Color(0xFFCCCCDD),
                disabledCheckedTrackColor   = Color(0xFFCCCCDD),
                disabledUncheckedTrackColor = Color(0xFFCCCCDD),
            )
        )
    }
}