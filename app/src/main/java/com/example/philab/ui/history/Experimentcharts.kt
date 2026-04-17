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
 * Estadísticas descriptivas de una serie de datos `(tiempo, valor)`.
 *
 * @property mean Media aritmética de los valores.
 * @property min Valor mínimo de la serie.
 * @property max Valor máximo de la serie.
 * @property error Semirango `(max - min) / 2`, usado como estimación de incertidumbre.
 */
private data class SeriesStats(
    val mean:  Float,
    val min:   Float,
    val max:   Float,
    val error: Float
) {
    companion object {
        /**
         * Calcula las estadísticas de una serie de pares `(tiempo, valor)`.
         *
         * @param series Lista de puntos de la serie.
         * @return [SeriesStats] calculadas, o `null` si la serie está vacía.
         */
        fun from(series: List<Pair<Float, Float>>): SeriesStats? {
            if (series.isEmpty()) return null
            val values = series.map { it.second }
            val mean   = values.average().toFloat()
            val min    = values.minOrNull() ?: return null
            val max    = values.maxOrNull() ?: return null
            return SeriesStats(mean = mean, min = min, max = max, error = (max - min) / 2f)
        }
    }
}

/**
 * Estado interno que encapsula todos los datos precalculados listos para renderizar
 * las tres gráficas cinemáticas.
 *
 * Se construye en un coroutine de fondo para no bloquear el hilo principal.
 *
 * @property data [ChartData] resultado del pipeline cinemático.
 * @property posProducer Productor de entradas Vico para la serie de posición.
 * @property velProducer Productor de entradas Vico para la serie de velocidad.
 * @property accelProducer Productor de entradas Vico para la serie de aceleración.
 * @property posTimeMap Mapa de índice de entrada a tiempo real en segundos para posición.
 * @property velTimeMap Mapa de índice de entrada a tiempo real en segundos para velocidad.
 * @property accelTimeMap Mapa de índice de entrada a tiempo real en segundos para aceleración.
 * @property posMinY Límite inferior del eje Y para la gráfica de posición.
 * @property posMaxY Límite superior del eje Y para la gráfica de posición.
 * @property velMinY Límite inferior del eje Y para la gráfica de velocidad.
 * @property velMaxY Límite superior del eje Y para la gráfica de velocidad.
 * @property accelMinY Límite inferior del eje Y para la gráfica de aceleración.
 * @property accelMaxY Límite superior del eje Y para la gráfica de aceleración.
 * @property posStats Estadísticas descriptivas de la serie de posición, o `null` si está vacía.
 * @property velStats Estadísticas descriptivas de la serie de velocidad, o `null` si está vacía.
 * @property accelStats Estadísticas descriptivas de la serie de aceleración, o `null` si está vacía.
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
    val posStats: SeriesStats?,
    val velStats:   SeriesStats?,
    val accelStats: SeriesStats?,
)

/**
 * Calcula el rango Y de una serie añadiendo un padding del 12 % a cada lado.
 *
 * @receiver Lista de pares `(tiempo, valor)`.
 * @return Par `(mínimo con padding, máximo con padding)`.
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
 * Expande el rango `[min, max]` alrededor de su centro por [factor], manteniendo
 * el centro fijo para que la curva quede centrada visualmente.
 *
 * Un factor de `1.0` no modifica el rango. Un factor de `5.0` produce un rango
 * cinco veces mayor, haciendo que la curva aparezca aplastada.
 *
 * @param min Límite inferior original del rango.
 * @param max Límite superior original del rango.
 * @param factor Factor de expansión.
 * @return Par `(nuevo mínimo, nuevo máximo)`.
 */
private fun expandYRange(min: Float, max: Float, factor: Float): Pair<Float, Float> {
    val center    = (min + max) / 2f
    val halfRange = (max - min) / 2f * factor
    return (center - halfRange) to (center + halfRange)
}

/**
 * Composable principal que muestra las gráficas cinemáticas de un experimento.
 *
 * Ejecuta el [KinematicPipeline] en un coroutine de fondo al recibir nuevos [results]
 * y muestra un indicador de progreso mientras los datos se procesan. Una vez listos,
 * presenta tres pestañas (posición, velocidad, aceleración) con su gráfica Vico,
 * estadísticas descriptivas y un slider de zoom vertical.
 *
 * Si los resultados corresponden al experimento de demostración integrado, los datos
 * de la gráfica se generan analíticamente en lugar de pasar por el pipeline.
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

                val posStats = SeriesStats.from(chart.position)
                val velStats   = SeriesStats.from(chart.velocity)
                val accelStats = SeriesStats.from(chart.acceleration)

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
                        posStats      = posStats,
                        velStats      = velStats,
                        accelStats    = accelStats,
                    )
                }
            } catch (_: Exception) { }
        }
    }

    var selectedTab  by remember { mutableStateOf(GraphTab.POSITION) }

    val yScaleFactors = remember {
        mutableStateMapOf(
            GraphTab.POSITION to 1f,
            GraphTab.VELOCITY to 5f,
            GraphTab.ACCEL    to 5f
        )
    }
    val currentScale = yScaleFactors[selectedTab] ?: 1f

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

            val state = readyState
            if (state != null) {
                val activeStats = when (selectedTab) {
                    GraphTab.POSITION -> state.posStats
                    GraphTab.VELOCITY -> state.velStats
                    GraphTab.ACCEL    -> state.accelStats
                }

                val yUnit = when (selectedTab) {
                    GraphTab.POSITION -> results.unit
                    GraphTab.VELOCITY -> "${results.unit}/s"
                    GraphTab.ACCEL    -> "${results.unit}/s²"
                }

                if (activeStats != null) {
                    SeriesStatsRow(
                        stats       = activeStats,
                        unit        = yUnit,
                        accentColor = selectedTab.color
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
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
 * Slider que permite hacer zoom-out vertical sobre la gráfica activa.
 *
 * Un factor de `1×` muestra el rango original con padding. Un factor de `10×`
 * expande el rango diez veces, aplastando la curva hacia el centro.
 * Solo se muestra cuando el rango Y está fijado, es decir, con datos reales
 * y no con el modo demo en autoescala.
 *
 * @param scaleFactor Factor de escala vertical actual, en el rango `[1, 10]`.
 * @param onScaleChange Callback invocado cuando el usuario mueve el slider.
 * @param accentColor Color de acento del slider, sincronizado con la pestaña activa.
 * @param modifier Modificador opcional de Compose.
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
 * Fila de estadísticas descriptivas para la serie activa.
 *
 * Muestra el valor medio con su semirango como incertidumbre y el rango completo
 * `[min, max]` de la serie, usando el símbolo cinemático correspondiente a [unit].
 *
 * @param stats Estadísticas de la serie activa.
 * @param unit Unidad de medida con la que se etiquetan los valores.
 * @param accentColor Color de acento para el texto principal.
 * @param modifier Modificador opcional de Compose.
 */
@Composable
private fun SeriesStatsRow(
    stats:       SeriesStats,
    unit:        String,
    accentColor: Color,
    modifier:    Modifier = Modifier
) {
    val (valueLine, rangeLine) = remember(stats, unit) {
        val sym = when {
            unit.contains("s²") || unit.contains("s2") -> "a"
            unit.contains("/s")                         -> "v"
            else                                        -> "x"
        }
        val mean  = "%.2f".format(stats.mean)
        val error = "%.2f".format(stats.error)
        val min   = "%.2f".format(stats.min)
        val max   = "%.2f".format(stats.max)
        "$sym = $mean ± $error $unit" to "Rango: [$min, $max] $unit"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = DividerColor,
            modifier  = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text       = valueLine,
            color      = accentColor,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            text     = rangeLine,
            color    = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * Genera un [ChartData] analítico para el experimento de demostración integrado.
 *
 * Simula un movimiento uniformemente acelerado con velocidad inicial [v0] y
 * aceleración constante [a] durante [n] fotogramas a 23 fps, produciendo
 * series perfectamente suaves sin necesidad de pasar por el pipeline.
 *
 * @param results Resultados originales, usados únicamente para obtener la unidad de medida.
 * @return [ChartData] con las series de posición, velocidad y aceleración generadas analíticamente.
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

/**
 * Crea y recuerda un [AxisValueFormatter] para el eje X que traduce índices de entrada
 * a tiempos reales en segundos usando [timeMap].
 *
 * @param timeMap Mapa de índice de entrada Vico a tiempo en segundos.
 * @return Formateador listo para usar en el eje inferior de Vico.
 */
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

/**
 * Convierte una serie de pares `(tiempo, valor)` en entradas Vico indexadas.
 *
 * Si la lista está vacía devuelve una entrada ficticia en el origen para
 * evitar que Vico falle con un modelo vacío.
 */
private fun List<Pair<Float, Float>>.safeEntries() =
    if (isEmpty()) listOf(entryOf(0f, 0f))
    else mapIndexed { index, (_, value) ->
        entryOf(index.toFloat(), value.safeY())
    }

/**
 * Construye un mapa de índice de entrada Vico a tiempo real en segundos.
 */
private fun List<Pair<Float, Float>>.timeMap(): Map<Int, Float> =
    mapIndexed { index, (tSec, _) -> index to tSec }.toMap()

/**
 * Devuelve el valor si es finito, o `0f` como fallback para evitar que Vico
 * renderice entradas con `NaN` o infinito.
 */
private fun Float.safeY(): Float =
    if (isFinite()) this else 0f

/**
 * Chip de selección de pestaña para las gráficas cinemáticas.
 *
 * Anima el color de fondo y del texto al cambiar el estado de selección.
 *
 * @param tab Pestaña que representa este chip.
 * @param selected `true` si esta pestaña está actualmente seleccionada.
 * @param onClick Callback invocado al pulsar el chip.
 * @param modifier Modificador opcional de Compose.
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
 * Composable que renderiza una gráfica de línea usando la librería Vico.
 *
 * Configura el estilo visual de la línea, el gradiente de relleno, los ejes,
 * el marcador interactivo con etiqueta de valor y el rango Y fijo opcional.
 *
 * @param producer [ChartEntryModelProducer] con los datos de la serie a graficar.
 * @param lineColor Color principal de la línea y el marcador.
 * @param gradientTop Color superior del gradiente de relleno bajo la línea.
 * @param gradientBottom Color inferior del gradiente de relleno bajo la línea.
 * @param xFormatter Formateador para las etiquetas del eje X.
 * @param yUnit Unidad de medida mostrada en el marcador al tocar un punto.
 * @param fixedMinY Límite inferior fijo del eje Y, o `null` para autoescala.
 * @param fixedMaxY Límite superior fijo del eje Y, o `null` para autoescala.
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