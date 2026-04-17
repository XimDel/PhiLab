package com.example.philab.ui.lab.experiment.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.core.calibration.CalibrationState
import java.util.Locale
import kotlin.math.max

/**
 * Overlay de calibración ArUco dibujado sobre el preview de cámara.
 *
 * Superpone dos elementos visuales:
 * - Un panel de texto en la esquina superior izquierda con el estado actual de
 *   la calibración (calibrado, buscando o error), con colores codificados por estado.
 * - Cuando el estado es [CalibrationState.Calibrated] con exactamente 4 esquinas
 *   detectadas, dibuja el contorno del marcador ArUco como cuatro segmentos azules
 *   con vértices amarillos, mapeados al espacio de pantalla mediante la misma
 *   transformación `fit-center` que usa CameraX.
 *
 * Si el estado es [CalibrationState.Idle], el composable renderiza un [Box] vacío
 * sin ningún contenido visible.
 *
 * @param calibrationState Estado actual de la calibración ArUco.
 * @param viewSize         Tamaño en píxeles del componente de preview en pantalla,
 *                         necesario para escalar las coordenadas del bitmap.
 * @param modifier         Modificador opcional aplicado al contenedor raíz.
 */
@Composable
fun MeasurementOverlay(
    calibrationState: CalibrationState,
    viewSize: IntSize,
    modifier: Modifier = Modifier
) {
    if (calibrationState is CalibrationState.Idle) {
        Box(modifier = modifier.fillMaxSize()) {}
        return
    }

    val lines = buildLines(calibrationState)

    Box(modifier = modifier.fillMaxSize()) {

        if (
            calibrationState is CalibrationState.Calibrated &&
            calibrationState.corners.size == 4 &&
            viewSize.width > 0 && viewSize.height > 0
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val corners = calibrationState.corners

                val srcW = calibrationState.bitmapWidth.toFloat()
                val srcH = calibrationState.bitmapHeight.toFloat()
                val dstW = viewSize.width.toFloat()
                val dstH = viewSize.height.toFloat()

                val scale   = max(dstW / srcW, dstH / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val dx = (dstW - scaledW) / 2f
                val dy = (dstH - scaledH) / 2f

                fun mapX(x: Float) = x * scale + dx
                fun mapY(y: Float) = y * scale + dy

                val mapped = corners.map { Offset(mapX(it.x), mapY(it.y)) }

                drawLine(Color.Blue, mapped[0], mapped[1], strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(Color.Blue, mapped[1], mapped[2], strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(Color.Blue, mapped[2], mapped[3], strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(Color.Blue, mapped[3], mapped[0], strokeWidth = 4f, cap = StrokeCap.Round)

                mapped.forEach { corner ->
                    drawCircle(color = Color.Yellow, radius = 6f, center = corner)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 30.dp, start = 14.dp)
                .wrapContentWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            lines.forEachIndexed { index, line ->
                val color = when {
                    calibrationState is CalibrationState.Calibrated && index == 0 -> Color(0xFF26D9A0)
                    calibrationState is CalibrationState.Searching  && index == 0 -> Color(0xFFFFB74D)
                    calibrationState is CalibrationState.Error      && index == 0 -> Color(0xFFEF5350)
                    calibrationState is CalibrationState.Error      && index == 1 -> Color(0xFFEF5350)
                    else -> Color.White
                }
                Text(text = line, color = color, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Construye la lista de líneas de texto para el panel de estado del overlay
 * según el [calibrationState] recibido.
 *
 * @param calibrationState Estado actual de la calibración.
 * @return Lista de cadenas a renderizar en el panel; vacía si el estado es [CalibrationState.Idle].
 */
private fun buildLines(calibrationState: CalibrationState): List<String> {
    return when (calibrationState) {
        is CalibrationState.Idle      -> emptyList()
        is CalibrationState.Searching -> listOf("ArUco: No detectado", "Medición en Pixeles")
        is CalibrationState.Calibrated -> listOf(
            "Estado: Calibrado",
            "Escala: ${formatScale(calibrationState.cmPerPx)} cm/px",
            "Marcador: ${formatMarker(calibrationState.markerSizeCm)} cm"
        )
        is CalibrationState.Error -> listOf("ArUco: Error", calibrationState.message)
    }
}

/**
 * Formatea un factor de escala en cm/px con tres decimales y separador de punto.
 *
 * @param value Factor de escala a formatear.
 * @return Cadena formateada, p. ej. `"0.053"`.
 */
private fun formatScale(value: Float) = String.format(Locale.US, "%.3f", value)

/**
 * Formatea un tamaño de marcador en centímetros con un decimal y separador de punto.
 *
 * @param value Tamaño en centímetros a formatear.
 * @return Cadena formateada, p. ej. `"5.0"`.
 */
private fun formatMarker(value: Float) = String.format(Locale.US, "%.1f", value)