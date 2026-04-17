package com.example.philab.ui.lab.experiment.camera

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/**
 * Diálogo modal que muestra el resumen cinemático de una sesión grabada y
 * permite al usuario nombrar el experimento, editar la etiqueta del objeto
 * seguido, y guardar o descartar los resultados.
 *
 * El diálogo adapta su layout al modo retrato y al modo paisaje mediante
 * [LocalConfiguration], reduciendo márgenes e iconos en horizontal para
 * aprovechar mejor el espacio disponible.
 *
 * No puede cerrarse pulsando fuera de él (`onDismissRequest` está vacío),
 * obligando al usuario a elegir explícitamente entre "Guardar" y "Reiniciar".
 *
 * @param results   Resultados del experimento a mostrar: métricas cinemáticas,
 *                  unidades, calibración y etiqueta del objeto.
 * @param onSave    Callback invocado con `(nombreExperimento, etiquetaObjeto)` al
 *                  pulsar "Guardar". Los valores vacíos se sustituyen por sus
 *                  valores predeterminados antes de invocar el callback.
 * @param onRestart Callback invocado al pulsar "Reiniciar", sin guardar los resultados.
 */
@Composable
fun SessionSummaryDialog(
    results: ExperimentResults,
    onSave: (experimentName: String, label: String) -> Unit,
    onRestart: () -> Unit
) {
    var experimentNameValue by remember { mutableStateOf(TextFieldValue("Experimento")) }
    var editingExperimentName by remember { mutableStateOf(false) }
    val experimentNameFocusRequester = remember { FocusRequester() }

    var labelValue by remember { mutableStateOf(TextFieldValue(results.selectedLabel)) }
    var editingLabel by remember { mutableStateOf(false) }
    val labelFocusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.72f else 0.92f)
                .then(
                    if (isLandscape) Modifier.fillMaxHeight(0.90f)
                    else Modifier
                )
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 24.dp,
                        vertical = if (isLandscape) 12.dp else 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 40.dp else 56.dp)
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
                        modifier = Modifier.size(if (isLandscape) 26.dp else 34.dp)
                    )
                }

                Spacer(Modifier.height(if (isLandscape) 6.dp else 10.dp))

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
                    },
                    isLandscape = isLandscape
                )

                Spacer(Modifier.height(if (isLandscape) 4.dp else 6.dp))

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

                Spacer(Modifier.height(if (isLandscape) 12.dp else 20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgRow)
                ) {
                    MetricRow(
                        icon = Icons.Filled.Timer, iconTint = AccentBlue,
                        label = "Duración", value = formatDuration(results.durationMs),
                        unit = "", compact = isLandscape
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.DataArray, iconTint = AccentPurple,
                        label = "Muestras", value = "${results.sampleCount}",
                        unit = "pts · ${"%.1f".format(results.sampleRateHz)} Hz",
                        compact = isLandscape
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.Straighten, iconTint = AccentOrange,
                        label = "Distancia", value = "%.2f".format(results.totalDistanceCm),
                        unit = results.unit, compact = isLandscape
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.ArrowForward, iconTint = AccentTeal,
                        label = "Desplazamiento", value = "%.2f".format(results.displacementCm),
                        unit = results.unit, compact = isLandscape
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.Speed, iconTint = AccentRed,
                        label = "Velocidad media", value = "%.2f".format(results.avgSpeedCmS),
                        unit = "${results.unit}/s", compact = isLandscape
                    )
                    RowDivider()
                    MetricRow(
                        icon = Icons.Filled.TrendingUp, iconTint = AccentGreen,
                        label = "Aceleración media", value = "%.2f".format(results.avgAccelCmS2),
                        unit = "${results.unit}/s²", compact = isLandscape
                    )
                }

                if (!results.isCalibrated) {
                    Spacer(Modifier.height(if (isLandscape) 8.dp else 12.dp))
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

                Spacer(Modifier.height(if (isLandscape) 12.dp else 20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isLandscape) 40.dp else 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                    ) {
                        Text("Reiniciar", fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val finalExperimentName = experimentNameValue.text
                                .trim().ifBlank { "Experimento" }
                            val finalLabel = labelValue.text
                                .trim().ifBlank { results.selectedLabel }
                            onSave(finalExperimentName, finalLabel)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isLandscape) 40.dp else 48.dp),
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

/**
 * Título editable que alterna entre un texto estático con icono de edición y un
 * [BasicTextField] de una sola línea al ser pulsado.
 *
 * Cuando se activa la edición, el campo recibe el foco automáticamente mediante
 * un [LaunchedEffect]. Al confirmar con la acción IME `Done`, se invoca [onDone]
 * y se libera el foco.
 *
 * @param value          Estado actual del campo de texto.
 * @param editing        Indica si el campo está en modo edición.
 * @param focusRequester [FocusRequester] para solicitar el foco al entrar en edición.
 * @param onStartEdit    Callback invocado al pulsar el título en modo lectura.
 * @param onValueChange  Callback invocado con el nuevo [TextFieldValue] mientras se escribe.
 * @param onDone         Callback invocado al confirmar la edición con la tecla IME.
 * @param isLandscape    Si `true`, reduce el tamaño de fuente para el modo paisaje.
 */
@Composable
private fun EditableTitle(
    value: TextFieldValue,
    editing: Boolean,
    focusRequester: FocusRequester,
    onStartEdit: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    isLandscape: Boolean = false
) {
    val titleFontSize = if (isLandscape) 16.sp else 20.sp

    if (editing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = titleFontSize,
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
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Editar nombre",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(if (isLandscape) 12.dp else 14.dp)
            )
        }
    }
}

/**
 * Fila de métrica que muestra un icono coloreado, una etiqueta descriptiva y
 * el valor numérico con su unidad alineados horizontalmente.
 *
 * @param icon      Icono vectorial que representa la magnitud medida.
 * @param iconTint  Color de acento aplicado al icono y su fondo circular.
 * @param label     Nombre de la magnitud mostrado en color secundario.
 * @param value     Valor numérico formateado como cadena.
 * @param unit      Unidad de medida; se omite si la cadena está vacía.
 * @param compact   Si `true`, reduce tamaños de icono, fuentes y padding vertical
 *                  para el modo paisaje.
 */
@Composable
private fun MetricRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String,
    compact: Boolean = false
) {
    val iconBoxSize   = if (compact) 28.dp else 34.dp
    val iconSize      = if (compact) 14.dp else 18.dp
    val vertPad       = if (compact) 8.dp  else 12.dp
    val valueFontSize = if (compact) 13.sp else 15.sp
    val labelFontSize = if (compact) 12.sp else 13.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = vertPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(iconBoxSize)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, contentDescription = null,
                tint = iconTint, modifier = Modifier.size(iconSize)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label, color = TextSecondary,
            fontSize = labelFontSize, modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, color = TextPrimary, fontSize = valueFontSize, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(text = unit, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Divisor horizontal con padding lateral usado entre [MetricRow] dentro de la
 * tarjeta de métricas.
 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 14.dp),
        thickness = 0.5.dp,
        color     = DividerColor
    )
}

/**
 * Formatea una duración en milisegundos a una cadena legible.
 *
 * Si la duración supera el minuto, devuelve el formato `M:SS.cc`; de lo
 * contrario usa `SS.cc s`, donde `cc` son centésimas de segundo.
 *
 * @param ms Duración en milisegundos.
 * @return Cadena formateada, por ejemplo `"1:05.23"` o `"8.04 s"`.
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis  = (ms % 1000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, centis)
    else "%d.%02d s".format(seconds, centis)
}