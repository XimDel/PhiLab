package com.example.philab.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.R
import com.example.philab.domain.experiment.ExperimentResults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta
private val BgCard        = Color.White.copy(alpha = 0.85f)
private val BgRowEven     = Color.White.copy(alpha = 0.55f)
private val BgRowOdd      = Color.White.copy(alpha = 0.40f)
private val BgTableHeader = Color.White.copy(alpha = 0.70f)
private val AccentGreen   = Color(0xFF1D9E75)
private val AccentBlue    = Color(0xFF2196F3)
private val TextPrimary   = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF7A7A8C)
private val TextMuted     = Color(0xFFAAAAAC)
private val DividerColor  = Color(0xFFDDDDE8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    results: ExperimentResults,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val unit       = results.unit

    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet   by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())
    }

    if (showSheet) {
        ExportBottomSheet(
            results    = results,
            sheetState = sheetState,
            onDismiss  = { showSheet = false },
            onCsvSaved = { _, _ -> },
            onPdfSaved = { _, _ -> },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter      = painterResource(id = R.drawable.pl_resultsscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier     = Modifier.fillMaxSize()
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Resultados — ${results.selectedLabel}",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor         = Color.White.copy(alpha = 0.85f),
                        titleContentColor      = TextPrimary,
                        navigationIconContentColor = TextPrimary
                    )
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(vertical = 16.dp)
                ) {

                    // Advertencia sin calibración
                    if (!results.isCalibrated) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xCCFFF8E1))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⚠", fontSize = 13.sp)
                                Text(
                                    text  = "Sin calibración ArUco — valores en píxeles. " +
                                            "Para obtener unidades reales (cm) usa un marcador ArUco de tamaño conocido.",
                                    color = Color(0xFF795548),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Información de la sesión
                    item {
                        SectionTitle("Información de la sesión")
                        MetaCard {
                            MetaRow("Objeto",           results.selectedLabel)
                            RowDivider()
                            MetaRow("Fecha",            dateFormatter.format(Date(results.recordedAt)))
                            RowDivider()
                            MetaRow("Duración",         formatDuration(results.durationMs))
                            RowDivider()
                            MetaRow("Muestras",         "${results.sampleCount} pts")
                            RowDivider()
                            MetaRow("Frecuencia real",  "${"%.1f".format(results.sampleRateHz)} Hz")
                            RowDivider()
                            MetaRow("Unidad",           unit)
                            if (results.isCalibrated) {
                                RowDivider()
                                MetaRow("Escala", "${"%.4f".format(results.cmPerPx)} cm/px")
                            }
                        }
                    }

                    // Resultados cinemáticos
                    item {
                        SectionTitle("Resultados cinemáticos")
                        MetaCard {
                            MetaRow("Distancia total",   "${"%.2f".format(results.totalDistanceCm)} $unit")
                            RowDivider()
                            MetaRow("Desplazamiento",    "${"%.2f".format(results.displacementCm)} $unit")
                            RowDivider()
                            MetaRow("Velocidad media",   "${"%.2f".format(results.avgSpeedCmS)} $unit/s")
                            RowDivider()
                            MetaRow("Aceleración media", "${"%.2f".format(results.avgAccelCmS2)} $unit/s²")
                        }
                    }

                    // ── Gráficas — ExperimentCharts con el pipeline nuevo ────
                    item {
                        SectionTitle("Gráficas")
                        ExperimentCharts(results = results)
                    }

                    // Tabla de datos
                    item {
                        SectionTitle("Datos capturados (${results.sampleCount} puntos)")
                    }
                    item {
                        TableHeader(unit = unit)
                    }

                    val displayPoints = if (results.points.size > 500) {
                        val step = results.points.size / 500
                        results.points.filterIndexed { index, _ -> index % step == 0 }
                    } else results.points

                    itemsIndexed(displayPoints) { index, point ->
                        TableRow(
                            index  = index + 1,
                            t      = point.tSeconds,
                            x      = point.xCm,
                            y      = point.yCm,
                            isEven = index % 2 == 0,
                            isLast = index == displayPoints.lastIndex
                        )
                    }

                    if (results.points.size > 500) {
                        item {
                            Text(
                                text      = "Mostrando 500 de ${results.points.size} puntos. " +
                                        "Exporta a CSV para ver todos.",
                                color     = TextMuted,
                                fontSize  = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }

                // Barra inferior fija
                Surface(
                    shadowElevation = 8.dp,
                    color           = Color.White.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick  = onNavigateHome,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFEEEEF2))
                        ) {
                            Icon(
                                imageVector     = Icons.Filled.Home,
                                contentDescription = "Ir al inicio",
                                tint            = AccentGreen,
                                modifier        = Modifier.size(24.dp)
                            )
                        }

                        Button(
                            onClick  = { showSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            enabled  = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    color       = Color.White,
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Exportar (PDF / CSV)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 15.sp,
                                    color      = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tabla ─────────────────────────────────────────────────────────────────────

@Composable
private fun TableHeader(unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .background(BgTableHeader)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        TableCell(text = "#",         weight = 0.8f, header = true)
        TableCell(text = "t (s)",     weight = 1.5f, header = true)
        TableCell(text = "x ($unit)", weight = 1.5f, header = true)
        TableCell(text = "y ($unit)", weight = 1.5f, header = true)
    }
}

@Composable
private fun TableRow(
    index: Int, t: Float, x: Float, y: Float,
    isEven: Boolean, isLast: Boolean
) {
    val shape = if (isLast)
        RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
    else RoundedCornerShape(0.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isEven) BgRowEven else BgRowOdd)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        TableCell(text = "$index",         weight = 0.8f, color = TextMuted)
        TableCell(text = "%.3f".format(t), weight = 1.5f, color = TextPrimary)
        TableCell(text = "%.2f".format(x), weight = 1.5f, color = AccentGreen)
        TableCell(text = "%.2f".format(y), weight = 1.5f, color = AccentBlue)
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    color: Color = TextPrimary
) {
    Text(
        text       = text,
        modifier   = Modifier.weight(weight),
        color      = if (header) TextSecondary else color,
        fontSize   = 12.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign  = TextAlign.Center
    )
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text          = text.uppercase(),
        color         = TextSecondary,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}

@Composable
private fun MetaCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            content  = content
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary,   fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.RowDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
}

private fun formatDuration(ms: Long): String {
    val s  = ms / 1000
    val m  = s / 60
    val sc = s % 60
    val cs = (ms % 1000) / 10
    return if (m > 0) "%d:%02d.%02d".format(m, sc, cs)
    else "%d.%02d s".format(sc, cs)
}