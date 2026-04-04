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
import com.example.philab.domain.experiment.MotionClassifier

// ── Paleta ────────────────────────────────────────────────────────────────────
private val BgCard        = Color.White.copy(alpha = 0.85f)
private val AccentGreen   = Color(0xFF1D9E75)
private val AccentBlue    = Color(0xFF2196F3)
private val AccentOrange  = Color(0xFFFF9800)
private val TextSecondary = Color(0xFF7A7A8C)
private val TextMuted     = Color(0xFFAAAAAC)
private val DividerColor  = Color(0xFFDDDDE8)

// ── Enum de pestañas ──────────────────────────────────────────────────────────
private enum class GraphTab(val label: String, val emoji: String, val color: Color) {
    POSITION("Posición",    "📍", AccentGreen),
    VELOCITY("Velocidad",   "⚡", AccentBlue),
    ACCEL   ("Aceleración", "🚀", AccentOrange)
}

// ── Estado inmutable con producers ya poblados ────────────────────────────────
private class ReadyChartState(
    val data: ChartData,
    val posProducer: ChartEntryModelProducer,
    val velProducer: ChartEntryModelProducer,
    val accelProducer: ChartEntryModelProducer,
    val posTimeMap: Map<Int, Float>,
    val velTimeMap: Map<Int, Float>,
    val accelTimeMap: Map<Int, Float>
)

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTE PRINCIPAL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExperimentCharts(
    results: ExperimentResults,
    modifier: Modifier = Modifier
) {
    // Demo: pelota con exactamente 71 puntos a 23 Hz
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

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    readyState = ReadyChartState(
                        data          = chart,
                        posProducer   = posProducer,
                        velProducer   = velProducer,
                        accelProducer = accelProducer,
                        posTimeMap    = posTimeMap,
                        velTimeMap    = velTimeMap,
                        accelTimeMap  = accelTimeMap
                    )
                }
            } catch (e: Exception) { }
        }
    }

    var selectedTab by remember { mutableStateOf(GraphTab.POSITION) }

    Card(
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {

            // Selector de pestañas
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

            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            Spacer(Modifier.height(10.dp))

            // Subtítulo
            val motionType = remember(results) { MotionClassifier.classify(results) }
            val subtitle = when (selectedTab) {
                GraphTab.POSITION -> motionType
                GraphTab.VELOCITY -> motionType
                GraphTab.ACCEL    -> motionType
            }
            Text(
                text     = subtitle,
                color    = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))

            // Contenido
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
                        GraphTab.POSITION -> results.unit
                        GraphTab.VELOCITY -> "${results.unit}/s"
                        GraphTab.ACCEL    -> "${results.unit}/s²"
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

                    if (pointCount > 1) {
                        val xFormatter = rememberTimeFormatter(timeMap)
                        VicoLineChart(
                            producer       = producer,
                            lineColor      = selectedTab.color,
                            gradientTop    = selectedTab.color.copy(alpha = 0.28f),
                            gradientBottom = Color.Transparent,
                            xFormatter     = xFormatter,
                            yUnit          = yUnit,
                            fixedMinY      = if (isDemo && selectedTab == GraphTab.ACCEL) 0f   else null,
                            fixedMaxY      = if (isDemo && selectedTab == GraphTab.ACCEL) 3.5f else null
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text      = "Datos insuficientes para esta gráfica",
                                color     = TextMuted,
                                fontSize  = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Series del demo

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

// ── Formatter eje X ───────────────────────────────────────────────────────────

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

// ── Extensiones ───────────────────────────────────────────────────────────────

private fun List<Pair<Float, Float>>.safeEntries() =
    if (isEmpty()) listOf(entryOf(0f, 0f))
    else mapIndexed { index, (_, value) ->
        entryOf(index.toFloat(), value.safeY())
    }

private fun List<Pair<Float, Float>>.timeMap(): Map<Int, Float> =
    mapIndexed { index, (tSec, _) -> index to tSec }.toMap()

private fun Float.safeY(): Float =
    if (isFinite()) this else 0f

// ── Chip de pestaña ───────────────────────────────────────────────────────────

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

// ── Gráfica Vico ──────────────────────────────────────────────────────────────

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

    val valuesOverrider = remember(fixedMinY, fixedMaxY) {
        if (fixedMinY != null && fixedMaxY != null) {
            AxisValuesOverrider.fixed(minY = fixedMinY, maxY = fixedMaxY)
        } else {
            AxisValuesOverrider.adaptiveYValues(yFraction = 1.0f, round = false)
        }
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
        marker             = marker,
        runInitialAnimation = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
    )

}