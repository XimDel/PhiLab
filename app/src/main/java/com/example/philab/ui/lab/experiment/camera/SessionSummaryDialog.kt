package com.example.philab.ui.lab.experiment.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.philab.domain.experiment.ExperimentResults

private val BgCard        = Color(0xFFFFFFFF)
private val BgRow         = Color(0xFFF7F7FA)
private val AccentGreen   = Color(0xFF1D9E75)
private val AccentBlue    = Color(0xFF2196F3)
private val AccentOrange  = Color(0xFFFF9800)
private val AccentPurple  = Color(0xFF9C27B0)
private val AccentRed     = Color(0xFFE53935)
private val AccentTeal    = Color(0xFF00897B)
private val TextPrimary   = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF7A7A8C)
private val DividerColor  = Color(0xFFEEEEF2)

@Composable
fun SessionSummaryDialog(
    results: ExperimentResults,
    onSave: (experimentName: String, label: String) -> Unit,
    onRestart: () -> Unit
) {
    // ── Estado editable: nombre del experimento ───────────────────────────────
    var experimentNameValue by remember { mutableStateOf(TextFieldValue("Experimento")) }
    var editingExperimentName by remember { mutableStateOf(false) }
    val experimentNameFocusRequester = remember { FocusRequester() }

    // ── Estado editable: nombre del objeto ───────────────────────────────────
    var labelValue by remember { mutableStateOf(TextFieldValue(results.selectedLabel)) }
    var editingLabel by remember { mutableStateOf(false) }
    val labelFocusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header: ícono ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(AccentGreen.copy(alpha = 0.2f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Nombre del experimento (editable, reemplaza "Experimento finalizado") ──
                EditableTitle(
                    value = experimentNameValue,
                    editing = editingExperimentName,
                    focusRequester = experimentNameFocusRequester,
                    onStartEdit = {
                        editingExperimentName = true
                        experimentNameValue = TextFieldValue("")
                    },
                    onValueChange = { experimentNameValue = it },
                    onDone = {
                        editingExperimentName = false
                        focusManager.clearFocus()
                    }
                )

                Spacer(Modifier.height(6.dp))

                // ── Chip editable: nombre del objeto ──────────────────────────
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (editingLabel) AccentGreen.copy(alpha = 0.18f)
                            else AccentGreen.copy(alpha = 0.10f)
                        )
                        .clickable {
                            if (!editingLabel) {
                                editingLabel = true
                                labelValue = TextFieldValue("")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (editingLabel) {
                        BasicTextField(
                            value = labelValue,
                            onValueChange = { labelValue = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                editingLabel = false
                                focusManager.clearFocus()
                            }),
                            modifier = Modifier
                                .focusRequester(labelFocusRequester)
                                .widthIn(min = 60.dp)
                        )
                        LaunchedEffect(Unit) { labelFocusRequester.requestFocus() }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = labelValue.text,
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar objeto",
                                tint = AccentGreen.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Métricas ──────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgRow)
                ) {
                    MetricRow(
                        icon = Icons.Filled.Timer,
                        iconTint = AccentBlue,
                        label = "Duración",
                        value = formatDuration(results.durationMs),
                        unit = ""
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.DataArray,
                        iconTint = AccentPurple,
                        label = "Muestras",
                        value = "${results.sampleCount}",
                        unit = "pts · ${"%.1f".format(results.sampleRateHz)} Hz"
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.Straighten,
                        iconTint = AccentOrange,
                        label = "Distancia",
                        value = "%.2f".format(results.totalDistanceCm),
                        unit = results.unit
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.ArrowForward,
                        iconTint = AccentTeal,
                        label = "Desplazamiento",
                        value = "%.2f".format(results.displacementCm),
                        unit = results.unit
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.Speed,
                        iconTint = AccentRed,
                        label = "Velocidad media",
                        value = "%.2f".format(results.avgSpeedCmS),
                        unit = "${results.unit}/s"
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.TrendingUp,
                        iconTint = AccentGreen,
                        label = "Aceleración media",
                        value = "%.2f".format(results.avgAccelCmS2),
                        unit = "${results.unit}/s²"
                    )
                }

                // ── Advertencia sin calibración ───────────────────────────────
                if (!results.isCalibrated) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF8E1))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF9A825),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sin calibración ArUco — valores en píxeles",
                            color = Color(0xFF795548),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Acciones ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                    ) {
                        Text("Reiniciar", fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val finalExperimentName = experimentNameValue.text
                                .trim()
                                .ifBlank { "Experimento" }
                            val finalLabel = labelValue.text
                                .trim()
                                .ifBlank { results.selectedLabel }
                            onSave(finalExperimentName, finalLabel)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Título editable (reemplaza "Experimento finalizado") ──────────────────────

@Composable
private fun EditableTitle(
    value: TextFieldValue,
    editing: Boolean,
    focusRequester: FocusRequester,
    onStartEdit: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit
) {
    if (editing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .focusRequester(focusRequester)
                .widthIn(min = 120.dp)
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { onStartEdit() }
        ) {
            Text(
                text = value.text.ifBlank { "Experimento" },
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Editar nombre",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

@Composable
private fun MetricRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(text = unit, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        thickness = 0.5.dp,
        color = DividerColor
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, centis)
    else "%d.%02d s".format(seconds, centis)
}