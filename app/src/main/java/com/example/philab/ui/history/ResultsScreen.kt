package com.example.philab.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.domain.experiment.ExperimentResults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    results: ExperimentResults,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val unit = results.unit

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados — ${results.selectedLabel}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0F1E)
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // Advertencia sin calibración
            if (!results.isCalibrated) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A00)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠  Sin calibración ArUco — valores en píxeles. " +
                                    "Para obtener unidades reales (cm) usa un marcador ArUco de tamaño conocido.",
                            color = Color(0xFFFFCC44),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Información de la sesión
            item {
                SectionTitle("Información de la sesión")
                MetaCard {
                    MetaRow("Objeto", results.selectedLabel)
                    MetaRow("Grabado", dateFormatter.format(Date(results.recordedAt)))
                    MetaRow("Duración", formatDuration(results.durationMs))
                    MetaRow("Muestras", "${results.sampleCount} pts")
                    MetaRow("Frecuencia real", "${"%.1f".format(results.sampleRateHz)} Hz")
                    MetaRow("Unidad", unit)
                    if (results.isCalibrated) {
                        MetaRow("Escala", "${"%.4f".format(results.cmPerPx)} cm/px")
                    }
                }
            }

            // Resultados cinemáticos
            item {
                SectionTitle("Resultados cinemáticos")
                MetaCard {
                    MetaRow("Distancia total", "${"%.2f".format(results.totalDistanceCm)} $unit")
                    MetaRow("Desplazamiento neto", "${"%.2f".format(results.displacementCm)} $unit")
                    MetaRow("Velocidad media", "${"%.2f".format(results.avgSpeedCmS)} $unit/s")
                    MetaRow("Aceleración media", "${"%.2f".format(results.avgAccelCmS2)} $unit/s²")
                }
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
                    index = index + 1,
                    t = point.tSeconds,
                    x = point.xCm,
                    y = point.yCm,
                    isEven = index % 2 == 0
                )
            }

            if (results.points.size > 500) {
                item {
                    Text(
                        text = "Mostrando 500 de ${results.points.size} puntos. " +
                                "Exporta a CSV para ver todos.",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75))
                ) {
                    Text(
                        "Exportar (PDF / CSV)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TableHeader(unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A4A), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        TableCell(text = "#", weight = 0.8f, header = true)
        TableCell(text = "t (s)", weight = 1.5f, header = true)
        TableCell(text = "x ($unit)", weight = 1.5f, header = true)
        TableCell(text = "y ($unit)", weight = 1.5f, header = true)
    }
}

@Composable
private fun TableRow(index: Int, t: Float, x: Float, y: Float, isEven: Boolean) {
    val bg = if (isEven) Color(0xFF16162A) else Color(0xFF1E1E36)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        TableCell(text = "$index", weight = 0.8f, color = Color(0xFF666688))
        TableCell(text = "%.3f".format(t), weight = 1.5f)
        TableCell(text = "%.2f".format(x), weight = 1.5f, color = Color(0xFF9FE1CB))
        TableCell(text = "%.2f".format(y), weight = 1.5f, color = Color(0xFFB5D4F4))
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    color: Color = Color.White
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = if (header) Color(0xFFCCCCEE) else color,
        fontSize = 12.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF9F9FBF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}

@Composable
private fun MetaCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8888AA), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, centis)
    else "%d.%02d s".format(seconds, centis)
}