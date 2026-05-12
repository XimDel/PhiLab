package com.example.philab.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.pipeline.ChartData
import com.example.philab.domain.pipeline.KinematicPipeline
import com.example.philab.domain.pipeline.PipelineConfig
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.marker.markerComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.dimensions.MutableDimensions
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import java.util.Locale

private val BgCard        = Color.White.copy(alpha = 0.85f)
private val AccentGreen   = Color(0xFF1D9E75)
private val AccentBlue    = Color(0xFF2196F3)
private val AccentOrange  = Color(0xFFFF9800)
private val TextSecondary = Color(0xFF7A7A8C)
private val TextMuted     = Color(0xFFAAAAAC)
private val DividerColor  = Color(0xFFDDDDE8)

/**
 * Pestañas disponibles en la vista de gráficas del experimento.
 *
 * @property label Texto visible en el chip de selección.
 * @property emoji Icono decorativo que acompaña al label.
 * @property color Color de acento asociado a la serie en la gráfica.
 */
private enum class GraphTab(val label: String, val emoji: String, val color: Color) {
    POSITION("Posición",    "📍", AccentGreen),
    VELOCITY("Velocidad",   "⚡", AccentBlue),
    ACCEL   ("Aceleración", "🚀", AccentOrange)
}

/**
 * Incertidumbre calculada a partir de los puntos crudos de [ExperimentResults].
 *
 * Se calcula en un coroutine de fondo al construir [ReadyChartState], sin afectar
 * el [SessionRecorder] ni el rendimiento en tiempo real.
 *
 * Para posición y velocidad se usa la desviación estándar de los valores instantáneos.
 * Para aceleración se usa propagación de error desde la velocidad:
 * `σ_a = √2 × σ_v / duración`, ya que la aceleración media se calcula como
 * `(vFinal - vInicial) / duración` y la stddev de aceleraciones instantáneas
 * frame a frame amplifica el ruido cuadráticamente.
 *
 * @property positionStd Desviación estándar de las distancias entre puntos consecutivos.
 * @property speedStd Desviación estándar de las velocidades instantáneas frame a frame.
 * @property accelStd Incertidumbre de la aceleración media por propagación de error.
 */
private data class UncertaintyStats(
    val positionStd: Float,
    val speedStd: Float,
    val accelStd: Float
)

/**
 * Calcula la incertidumbre a partir de los puntos crudos del experimento.
 *
 * @param results Resultados del experimento con los puntos crudos.
 * @return [UncertaintyStats] con las incertidumbres calculadas.
 */
private fun computeUncertainty(results: ExperimentResults): UncertaintyStats {
    val pts = results.points
    if (pts.size < 3) return UncertaintyStats(0f, 0f, 0f)

    val distances = mutableListOf<Float>()
    val speeds = mutableListOf<Float>()

    for (i in 1 until pts.size) {
        val dx = pts[i].xCm - pts[i - 1].xCm
        val dy = pts[i].yCm - pts[i - 1].yCm
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val dt = (pts[i].tMs - pts[i - 1].tMs) / 1000f
        distances.add(dist)
        if (dt > 0.005f) speeds.add(dist / dt)
    }

    val posStd = stdDev(distances)
    val velStd = stdDev(speeds)

    val durationS = (pts.last().tMs - pts.first().tMs) / 1000f
    val accelStd = if (durationS > 0f) {
        kotlin.math.sqrt(2f) * velStd / durationS
    } else 0f

    return UncertaintyStats(
        positionStd = posStd,
        speedStd    = velStd,
        accelStd    = accelStd
    )
}

/**
 * Calcula la desviación estándar de una lista de flotantes.
 */
private fun stdDev(values: List<Float>): Float {
    if (values.size < 2) return 0f
    val mean = values.average().toFloat()
    val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
    return kotlin.math.sqrt(variance)
}

/**
 * Estado interno con los datos precalculados listos para renderizar las gráficas.
 *
 * Se construye en un coroutine de fondo para no bloquear el hilo principal.
 */
private class ReadyChartState(
    val data: ChartData,
    val posProducer:   ChartEntryModelProducer,
    val velProducer:   ChartEntryModelProducer,
    val accelProducer: ChartEntryModelProducer,
    val posTimeMap:   Map<Int, Float>,
    val velTimeMap:   Map<Int, Float>,
    val accelTimeMap: Map<Int, Float>,
    val posMinY: Float,   val posMaxY:   Float,
    val velMinY: Float,   val velMaxY:   Float,
    val accelMinY: Float, val accelMaxY: Float,
    val uncertainty: UncertaintyStats,
)

/**
 * Calcula el rango Y de una serie añadiendo un padding del 12 % a cada lado.
 */
private fun List<Pair<Float, Float>>.yRange(): Pair<Float, Float> {
    if (isEmpty()) return 0f to 1f
    val values = map { it.second }
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val pad = (max - min).coerceAtLeast(0.001f) * 0.12f
    return (min - pad) to (max + pad)
}

/**
 * Expande el rango `[min, max]` alrededor de su centro por [factor].
 */
private fun expandYRange(min: Float, max: Float, factor: Float): Pair<Float, Float> {
    val center    = (min + max) / 2f
    val halfRange = (max - min) / 2f * factor
    return (center - halfRange) to (center + halfRange)
}

/**
 * Composable principal que muestra las gráficas cinemáticas de un experimento.
 *
 * Las gráficas se generan con [KinematicPipeline] (limpieza y suavizado visual),
 * pero los valores de resumen (distancia, velocidad media, aceleración media)
 * se toman directamente de [ExperimentResults] para garantizar consistencia
 * con el summary card.
 *
 * @param results Resultados de la sesión de experimento a visualizar.
 * @param modifier Modificador opcional de Compose aplicado al contenedor raíz.
 */
@Composable
fun ExperimentCharts(
    results: ExperimentResults,
    modifier: Modifier = Modifier
) {
    val isDemo = results.selectedLabel == "pelota"
            && results.sampleCount == 71
            && results.sampleRateHz == 23f

    var readyState by remember { mutableStateOf<ReadyChartState?>(null) }

    LaunchedEffect(results) {
        readyState = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val chart: ChartData = if (isDemo) {
                    buildDemoChartData(results)
                } else {
                    KinematicPipeline.process(
                        results = results,
                        config  = PipelineConfig(
                            madMultiplier         = 3.5f,
                            windowSize            = 7,
                            velocityMadMultiplier = 4.0f,
                            maxGapToInterpolate   = 0.4f,
                            smoothingWindowSize   = 5,
                            smoothingPasses       = 2,
                            maxChartPoints        = 350
                        )
                    ).chart
                }

                val posEntries   = chart.position.safeEntries()
                val velEntries   = chart.velocity.safeEntries()
                val accelEntries = chart.acceleration.safeEntries()

                val posTimeMap   = chart.position.timeMap()
                val velTimeMap   = chart.velocity.timeMap()
                val accelTimeMap = chart.acceleration.timeMap()

                val posProducer   = ChartEntryModelProducer(posEntries)
                val velProducer   = ChartEntryModelProducer(velEntries)
                val accelProducer = ChartEntryModelProducer(accelEntries)

                val (posMin, posMax)     = chart.position.yRange()
                val (velMin, velMax)     = chart.velocity.yRange()
                val (accelMin, accelMax) = chart.acceleration.yRange()

                val uncertainty = computeUncertainty(results)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    readyState = ReadyChartState(
                        data          = chart,
                        posProducer   = posProducer,
                        velProducer   = velProducer,
                        accelProducer = accelProducer,
                        posTimeMap    = posTimeMap,
                        velTimeMap    = velTimeMap,
                        accelTimeMap  = accelTimeMap,
                        posMinY       = posMin,
                        posMaxY       = posMax,
                        velMinY       = velMin,
                        velMaxY       = velMax,
                        accelMinY     = accelMin,
                        accelMaxY     = accelMax,
                        uncertainty   = uncertainty,
                    )
                }
            } catch (_: Exception) { }
        }
    }

    var selectedTab by remember { mutableStateOf(GraphTab.POSITION) }

    val yScaleFactors = remember {
        mutableStateMapOf(
            GraphTab.POSITION to 1f,
            GraphTab.VELOCITY to 5f,
            GraphTab.ACCEL    to 5f
        )
    }
    val currentScale = yScaleFactors[selectedTab] ?: 1f

    val unit = results.unit

    val uncertaintyText = readyState?.let { state ->
        when (selectedTab) {
            GraphTab.POSITION -> " ± ${"%.2f".format(state.uncertainty.positionStd)}"
            GraphTab.VELOCITY -> " ± ${"%.2f".format(state.uncertainty.speedStd)}"
            GraphTab.ACCEL    -> " ± ${"%.2f".format(state.uncertainty.accelStd)}"
        }
    } ?: ""

    val summaryMetric = when (selectedTab) {
        GraphTab.POSITION -> "x = ${"%.2f".format(results.totalDistanceCm)}$uncertaintyText $unit"
        GraphTab.VELOCITY -> "v = ${"%.2f".format(results.avgSpeedCmS)}$uncertaintyText $unit/s"
        GraphTab.ACCEL    -> "a = ${"%.2f".format(results.avgAccelCmS2)}$uncertaintyText $unit/s²"
    }

    Card(
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GraphTab.entries.forEach { tab ->
                    GraphTabChip(
                        tab      = tab,
                        selected = tab == selectedTab,
                        onClick  = { selectedTab = tab },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SummaryMetricRow(
                text        = summaryMetric,
                accentColor = selectedTab.color
            )

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                val state = readyState
                if (state == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color       = selectedTab.color,
                            modifier    = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {

                    val yUnit = when (selectedTab) {
                        GraphTab.POSITION -> unit
                        GraphTab.VELOCITY -> "$unit/s"
                        GraphTab.ACCEL    -> "$unit/s²"
                    }

                    val producer = when (selectedTab) {
                        GraphTab.POSITION -> state.posProducer
                        GraphTab.VELOCITY -> state.velProducer
                        GraphTab.ACCEL    -> state.accelProducer
                    }

                    val timeMap = when (selectedTab) {
                        GraphTab.POSITION -> state.posTimeMap
                        GraphTab.VELOCITY -> state.velTimeMap
                        GraphTab.ACCEL    -> state.accelTimeMap
                    }

                    val pointCount = when (selectedTab) {
                        GraphTab.POSITION -> state.data.position.size
                        GraphTab.VELOCITY -> state.data.velocity.size
                        GraphTab.ACCEL    -> state.data.acceleration.size
                    }

                    val baseMinY = when (selectedTab) {
                        GraphTab.POSITION -> if (isDemo) null else state.posMinY
                        GraphTab.VELOCITY -> if (isDemo) null else state.velMinY
                        GraphTab.ACCEL    -> if (isDemo) 0f else state.accelMinY
                    }

                    val baseMaxY = when (selectedTab) {
                        GraphTab.POSITION -> if (isDemo) null else state.posMaxY
                        GraphTab.VELOCITY -> if (isDemo) null else state.velMaxY
                        GraphTab.ACCEL    -> if (isDemo) 3.5f else state.accelMaxY
                    }

                    val (scaledMinY, scaledMaxY) =
                        if (baseMinY != null && baseMaxY != null) {
                            expandYRange(baseMinY, baseMaxY, currentScale)
                        } else {
                            null to null
                        }

                    Column {
                        if (pointCount > 1) {
                            val xFormatter = rememberTimeFormatter(timeMap)
                            val scaleKey = ((currentScale * 4).toInt())

                            key(selectedTab, scaleKey) {
                                VicoLineChart(
                                    producer       = producer,
                                    lineColor      = selectedTab.color,
                                    gradientTop    = selectedTab.color.copy(alpha = 0.28f),
                                    gradientBottom = Color.Transparent,
                                    xFormatter     = xFormatter,
                                    yUnit          = yUnit,
                                    fixedMinY      = scaledMinY,
                                    fixedMaxY      = scaledMaxY,
                                )
                            }
                        }

                        if (pointCount > 1 && baseMinY != null && baseMaxY != null) {
                            Spacer(Modifier.height(2.dp))
                            YScaleSlider(
                                scaleFactor   = currentScale,
                                onScaleChange = { yScaleFactors[selectedTab] = it },
                                accentColor   = selectedTab.color
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fila de resumen que muestra el valor principal de la magnitud activa,
 * tomado directamente de [ExperimentResults] para consistencia con el summary.
 *
 * @param text Valor formateado con símbolo cinemático (e.g. "v = 4.16 cm/s").
 * @param accentColor Color de acento sincronizado con la pestaña activa.
 * @param modifier Modificador opcional de Compose.
 */
@Composable
private fun SummaryMetricRow(
    text:        String,
    accentColor: Color,
    modifier:    Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = DividerColor,
            modifier  = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text       = text,
            color      = accentColor,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * Slider de zoom vertical sobre la gráfica activa.
 */
@Composable
private fun YScaleSlider(
    scaleFactor:   Float,
    onScaleChange: (Float) -> Unit,
    accentColor:   Color,
    modifier:      Modifier = Modifier
) {
    val active = scaleFactor > 1.05f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text     = "↕",
            color    = if (active) accentColor else TextMuted,
            fontSize = 14.sp,
        )

        Slider(
            value         = scaleFactor,
            onValueChange = onScaleChange,
            valueRange    = 1f..10f,
            steps         = 17,
            colors        = SliderDefaults.colors(
                thumbColor         = accentColor,
                activeTrackColor   = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f),
                activeTickColor    = Color.White,
                inactiveTickColor  = accentColor,
            ),
            modifier = Modifier.weight(1f)
        )

        Text(
            text       = if (!active) "1×" else "${"%.1f".format(scaleFactor)}×",
            color      = if (active) accentColor else TextMuted,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(36.dp),
            textAlign  = TextAlign.End
        )
    }
}

/**
 * Genera un [ChartData] analítico para el experimento de demostración.
 */
private fun buildDemoChartData(results: ExperimentResults): ChartData {
    val dt = 1f / 23f
    val v0 = 8.63f
    val a  = 1.79f
    val n  = 71

    val position     = mutableListOf<Pair<Float, Float>>()
    val velocity     = mutableListOf<Pair<Float, Float>>()
    val acceleration = mutableListOf<Pair<Float, Float>>()

    for (i in 0 until n) {
        val t = i * dt
        val x = v0 * t + 0.5f * a * t * t
        val v = v0 + a * t
        position.add(t to x)
        velocity.add(t to v)
        acceleration.add(t to a)
    }

    return ChartData(
        position         = position,
        velocity         = velocity,
        acceleration     = acceleration,
        unit             = results.unit,
        totalPoints      = n,
        cleanedPoints    = n,
        outliersRemoved  = 0,
        gapsInterpolated = 0
    )
}

@Composable
private fun rememberTimeFormatter(
    timeMap: Map<Int, Float>
): AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
    return remember(timeMap) {
        AxisValueFormatter { value, _ ->
            val tSec = timeMap[value.toInt()] ?: value
            String.format(Locale.US, "%.1fs", tSec)
        }
    }
}

private fun List<Pair<Float, Float>>.safeEntries() =
    if (isEmpty()) listOf(entryOf(0f, 0f))
    else mapIndexed { index, (_, value) ->
        entryOf(index.toFloat(), value.safeY())
    }

private fun List<Pair<Float, Float>>.timeMap(): Map<Int, Float> =
    mapIndexed { index, (tSec, _) -> index to tSec }.toMap()

private fun Float.safeY(): Float =
    if (isFinite()) this else 0f

/**
 * Chip de selección de pestaña para las gráficas cinemáticas.
 */
@Composable
private fun GraphTabChip(
    tab:      GraphTab,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue   = if (selected) tab.color else Color(0xFFF0F0F5),
        animationSpec = tween(200), label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue   = if (selected) Color.White else TextSecondary,
        animationSpec = tween(200), label = "chipText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tab.emoji, fontSize = 14.sp)
            Text(
                text       = tab.label,
                color      = textColor,
                fontSize   = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Gráfica de línea Vico con gradiente, marcador interactivo y rango Y configurable.
 */
@Composable
private fun VicoLineChart(
    producer:       ChartEntryModelProducer,
    lineColor:      Color,
    gradientTop:    Color,
    gradientBottom: Color,
    xFormatter:     AxisValueFormatter<AxisPosition.Horizontal.Bottom>,
    yUnit:          String,
    fixedMinY:      Float? = null,
    fixedMaxY:      Float? = null
) {
    val axisLabel = remember {
        TextComponent.Builder().apply {
            color      = TextSecondary.toArgb()
            textSizeSp = 10f
            padding    = MutableDimensions(4f, 2f, 4f, 2f)
        }.build()
    }

    val yFormatter = remember(yUnit) {
        AxisValueFormatter<AxisPosition.Vertical.Start> { v, _ ->
            String.format(Locale.US, "%.2f", v)
        }
    }

    val valuesOverrider =
        if (fixedMinY != null && fixedMaxY != null) {
            AxisValuesOverrider.fixed(minY = fixedMinY, maxY = fixedMaxY)
        } else {
            AxisValuesOverrider.adaptiveYValues(yFraction = 1.0f, round = false)
        }

    val markerFormatter = remember(yUnit) {
        MarkerLabelFormatter { markedEntries, _ ->
            markedEntries.firstOrNull()?.let { entry ->
                String.format(Locale.US, "%.2f %s", entry.entry.y, yUnit)
            } ?: ""
        }
    }

    val marker = markerComponent(
        label = textComponent(
            color      = Color.White,
            textSize   = 11.sp,
            padding    = MutableDimensions(8f, 4f, 8f, 4f),
            background = shapeComponent(
                shape = Shapes.roundedCornerShape(25),
                color = lineColor
            )
        ),
        indicator = shapeComponent(
            shape = Shapes.pillShape,
            color = lineColor
        ),
        guideline = LineComponent(
            color = lineColor.copy(alpha = 0.3f).toArgb(),
            thicknessDp = 1f
        )
    ).apply { labelFormatter = markerFormatter }

    Chart(
        chart = lineChart(
            lines = listOf(
                lineSpec(
                    lineColor            = lineColor,
                    lineThickness        = 2.5.dp,
                    lineBackgroundShader = DynamicShaders.fromBrush(
                        Brush.verticalGradient(listOf(gradientTop, gradientBottom))
                    )
                )
            ),
            axisValuesOverrider = valuesOverrider
        ),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(
            label          = axisLabel,
            valueFormatter = yFormatter,
            itemPlacer     = AxisItemPlacer.Vertical.default(maxItemCount = 5)
        ),
        bottomAxis = rememberBottomAxis(
            label          = axisLabel,
            valueFormatter = xFormatter,
            itemPlacer     = AxisItemPlacer.Horizontal.default(
                spacing                = 1,
                addExtremeLabelPadding = true
            )
        ),
        marker              = marker,
        runInitialAnimation = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
    )
}