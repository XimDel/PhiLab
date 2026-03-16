package com.example.philab.ui.lab.experiment.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.philab.domain.experiment.ExperimentResults

@Composable
fun SessionSummaryDialog(
    results: ExperimentResults,
    onSave: () -> Unit,
    onRestart: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Título
                Text(
                    text = "Sesión finalizada",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = results.selectedLabel,
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(20.dp))
                Divider(color = Color(0xFF333355))
                Spacer(Modifier.height(16.dp))

                // Métricas principales
                SummaryRow(
                    label = "Duración",
                    value = formatDuration(results.durationMs),
                    unit = ""
                )
                SummaryRow(
                    label = "Muestras",
                    value = "${results.sampleCount}",
                    unit = "pts  ·  ${"%.1f".format(results.sampleRateHz)} Hz"
                )
                SummaryRow(
                    label = "Distancia",
                    value = "%.2f".format(results.totalDistanceCm),
                    unit = results.unit
                )
                SummaryRow(
                    label = "Desplazamiento",
                    value = "%.2f".format(results.displacementCm),
                    unit = results.unit
                )
                SummaryRow(
                    label = "Velocidad media",
                    value = "%.2f".format(results.avgSpeedCmS),
                    unit = "${results.unit}/s"
                )
                SummaryRow(
                    label = "Aceleración media",
                    value = "%.2f".format(results.avgAccelCmS2),
                    unit = "${results.unit}/s²"
                )

                // Advertencia si no había calibración
                if (!results.isCalibrated) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⚠ Sin calibración ArUco — valores en píxeles",
                            color = Color(0xFFFFCC44),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reiniciar
                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFAAAAAA)
                        )
                    ) {
                        Text("Reiniciar")
                    }

                    // Guardar
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D9E75)
                        )
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    color = Color(0xFF888888),
                    fontSize = 11.sp
                )
            }
        }
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