package com.example.philab.domain.experiment

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Registra una sesión de experimento de tracking en tiempo real.
 *
 * Acumula puntos de posición durante la grabación y, al finalizar con [stop],
 * aplica un pipeline de filtrado sobre la trayectoria antes de calcular las
 * métricas cinemáticas (distancia, velocidad, aceleración).
 *
 * Pipeline de filtrado en [stop]:
 * 1. Media móvil centrada de ventana [smoothWindowSize] sobre las coordenadas
 *    x e y, preservando los timestamps originales. Suaviza las oscilaciones de
 *    alta frecuencia del tracking sin afectar el movimiento real.
 * 2. Umbral de segmento mínimo [minSegmentCm]: segmentos más cortos que este
 *    valor se consideran ruido estático y no se suman a la distancia total.
 * 3. Cálculo de aceleración robusta usando ventanas del ~20% al inicio y final
 *    de la trayectoria, en vez de depender de un solo par de puntos.
 *
 * Ciclo de vida: [start] → [addPoint] (N veces) → [stop].
 *
 * @property smoothWindowSize Tamaño de la ventana de media móvil para suavizar
 *   la trayectoria. Valores más altos producen trayectorias más suaves pero
 *   pueden recortar movimientos rápidos. Default: 5.
 * @property minSegmentCm Distancia mínima en centímetros que debe tener un
 *   segmento entre puntos consecutivos para ser considerado movimiento real.
 *   Segmentos menores se descartan como ruido. Default: 0.15 cm.
 */
class SessionRecorder(
    private val smoothWindowSize: Int = 5,
    private val minSegmentCm: Float = 0.15f
) {

    /** Lista mutable de puntos de posición registrados durante la sesión activa. */
    private val points = mutableListOf<DataPoint>()

    /** Timestamp en nanosegundos del primer frame, usado como referencia temporal. */
    private var startTimeNs = 0L

    /** Timestamp en milisegundos de epoch del inicio de la sesión, para metadatos. */
    private var startEpochMs = 0L

    /** Flag que indica si hay una sesión de grabación en curso. */
    private var isRecording = false

    /** Etiqueta del objeto rastreado durante la sesión actual. */
    private var currentLabel = "Object"

    /** Factor de escala actual en centímetros por píxel, actualizable durante la sesión. */
    private var currentCmPerPx = 1f

    /** Unidad de medida actual (`"cm"` o `"px"`). */
    private var currentUnit = "px"

    /**
     * Inicia una nueva sesión de grabación, descartando cualquier dato previo.
     *
     * El parámetro [startFrameTimestampNs] debe ser el timestamp del sensor de cámara
     * (`ImageProxy.imageInfo.timestamp`), que es monotónico desde el arranque del dispositivo
     * y se usa exclusivamente para calcular el tiempo relativo de cada punto.
     * La fecha real de la sesión se registra con `System.currentTimeMillis()`.
     *
     * @param label Etiqueta del objeto que se va a rastrear durante la sesión.
     * @param cmPerPx Factor de escala inicial en centímetros por píxel.
     * @param unit Unidad de medida a usar en los resultados (`"cm"` o `"px"`).
     * @param startFrameTimestampNs Timestamp en nanosegundos del primer fotograma,
     *   tomado del sensor de cámara. Si es `0` se usa `System.nanoTime()`.
     */
    fun start(
        label: String,
        cmPerPx: Float,
        unit: String,
        startFrameTimestampNs: Long = 0L
    ) {
        points.clear()
        startTimeNs = if (startFrameTimestampNs > 0L) startFrameTimestampNs
        else System.nanoTime()
        startEpochMs = System.currentTimeMillis()
        currentLabel = label
        currentCmPerPx = cmPerPx
        currentUnit = unit
        isRecording = true
    }

    /**
     * Actualiza el factor de escala y la unidad de medida durante una sesión activa.
     *
     * No tiene efecto si no hay una sesión en curso.
     *
     * @param cmPerPx Nuevo factor de escala en centímetros por píxel.
     * @param unit Nueva unidad de medida (`"cm"` o `"px"`).
     */
    fun updateMetadata(cmPerPx: Float, unit: String) {
        if (!isRecording) return
        currentCmPerPx = cmPerPx
        currentUnit = unit
    }

    /**
     * Registra un punto de posición en la sesión activa.
     *
     * El tiempo relativo del punto se calcula a partir del timestamp del sensor de cámara,
     * evitando el jitter de hasta 40 ms que introduciría `System.currentTimeMillis()`.
     * No tiene efecto si no hay una sesión en curso.
     *
     * @param xCm Posición horizontal en centímetros, o en píxeles si no hay calibración.
     * @param yCm Posición vertical en centímetros, o en píxeles si no hay calibración.
     * @param frameTimestampNs Timestamp en nanosegundos del fotograma, del sensor de cámara.
     */
    fun addPoint(xCm: Float, yCm: Float, frameTimestampNs: Long = 0L) {
        if (!isRecording) return
        val tsNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        val tMs = (tsNs - startTimeNs) / 1_000_000L
        points.add(DataPoint(tMs = tMs.coerceAtLeast(0L), xCm = xCm, yCm = yCm))
    }

    /**
     * Detiene la sesión, filtra la trayectoria y calcula las métricas cinemáticas.
     *
     * Aplica el pipeline de filtrado (media móvil + umbral de segmento) sobre los
     * puntos crudos antes de calcular distancia total, desplazamiento neto, velocidad
     * media y aceleración media. Los puntos crudos originales se preservan en el
     * resultado para gráficos.
     *
     * @return [ExperimentResults] con los datos procesados, o `null` si la sesión
     *   no estaba activa, tiene menos de dos puntos o su duración es cero.
     */
    fun stop(): ExperimentResults? {
        if (!isRecording) return null
        isRecording = false

        if (points.size < 2) return null

        val durationMs = points.last().tMs - points.first().tMs
        if (durationMs <= 0) return null

        val smoothed = smoothTrajectory(points, smoothWindowSize)

        val durationS = durationMs / 1000f
        val sampleRateHz = points.size / durationS

        var distanciaTotal = 0f
        for (i in 1 until smoothed.size) {
            val dx = smoothed[i].xCm - smoothed[i - 1].xCm
            val dy = smoothed[i].yCm - smoothed[i - 1].yCm
            val segDist = sqrt(dx * dx + dy * dy)
            if (segDist >= minSegmentCm) {
                distanciaTotal += segDist
            }
        }

        val dxNet = smoothed.last().xCm - smoothed.first().xCm
        val dyNet = smoothed.last().yCm - smoothed.first().yCm
        val desplazamiento = sqrt(dxNet * dxNet + dyNet * dyNet)

        val velocidadMedia = if (durationS > 0) distanciaTotal / durationS else 0f

        val aceleracionMedia = computeRobustAcceleration(smoothed, durationS)

        return ExperimentResults(
            points           = points.toList(),
            unit             = currentUnit,
            selectedLabel    = currentLabel,
            recordedAt       = startEpochMs,
            durationMs       = durationMs,
            sampleCount      = points.size,
            sampleRateHz     = (sampleRateHz * 10).roundToInt() / 10f,
            cmPerPx          = currentCmPerPx,
            totalDistanceCm  = distanciaTotal,
            displacementCm   = desplazamiento,
            avgSpeedCmS      = velocidadMedia,
            avgAccelCmS2     = aceleracionMedia,
            minX = points.minOf { it.xCm },
            maxX = points.maxOf { it.xCm },
            minT = points.first().tSeconds,
            maxT = points.last().tSeconds
        )
    }

    /**
     * Aplica una media móvil centrada sobre las coordenadas de la trayectoria.
     *
     * Preserva los timestamps originales de cada punto. Los puntos en los extremos
     * de la lista usan ventanas asimétricas reducidas automáticamente.
     * Si la lista tiene menos de 3 puntos o la ventana es menor a 3, devuelve
     * la lista sin modificar.
     *
     * @param raw Lista de puntos crudos registrados durante la sesión.
     * @param windowSize Tamaño de la ventana de media móvil.
     * @return Lista de puntos suavizados con los mismos timestamps.
     */
    private fun smoothTrajectory(raw: List<DataPoint>, windowSize: Int): List<DataPoint> {
        if (raw.size < 3 || windowSize < 3) return raw
        val half = windowSize / 2
        return List(raw.size) { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(raw.size - 1)
            val count = to - from + 1
            var sumX = 0f; var sumY = 0f
            for (j in from..to) {
                sumX += raw[j].xCm
                sumY += raw[j].yCm
            }
            DataPoint(
                tMs = raw[i].tMs,
                xCm = sumX / count,
                yCm = sumY / count
            )
        }
    }

    /**
     * Calcula la aceleración media usando ventanas al inicio y final de la trayectoria.
     *
     * Calcula la velocidad promedio en el primer ~20% y último ~20% de los puntos
     * suavizados, y divide la diferencia por la duración total. Esto produce una
     * estimación más robusta que depender de un solo par de puntos, que sería
     * extremadamente sensible al ruido.
     *
     * @param smoothed Trayectoria suavizada por [smoothTrajectory].
     * @param durationS Duración total de la sesión en segundos.
     * @return Aceleración media estimada en cm/s². Retorna 0 si hay menos de 6 puntos.
     */
    private fun computeRobustAcceleration(smoothed: List<DataPoint>, durationS: Float): Float {
        if (smoothed.size < 6 || durationS <= 0) return 0f

        val windowSize = (smoothed.size * 0.2f).toInt().coerceIn(2, 10)

        val vInit = averageSpeed(smoothed, 0, windowSize)
        val vFinal = averageSpeed(smoothed, smoothed.size - windowSize - 1, windowSize)

        return (vFinal - vInit) / durationS
    }

    /**
     * Calcula la velocidad promedio en una ventana de puntos consecutivos.
     *
     * Suma la distancia euclidiana y el tiempo transcurrido entre cada par de puntos
     * dentro de la ventana, y devuelve la velocidad como distancia/tiempo.
     *
     * @param pts Lista completa de puntos de la trayectoria.
     * @param startIdx Índice del primer punto de la ventana.
     * @param count Número de segmentos (pares de puntos consecutivos) a incluir.
     * @return Velocidad promedio en cm/s dentro de la ventana. Retorna 0 si el tiempo es 0.
     */
    private fun averageSpeed(pts: List<DataPoint>, startIdx: Int, count: Int): Float {
        var totalDist = 0f
        var totalTimeS = 0f
        val from = startIdx.coerceAtLeast(0)
        val to = (from + count).coerceAtMost(pts.size - 1)
        for (i in from until to) {
            val dx = pts[i + 1].xCm - pts[i].xCm
            val dy = pts[i + 1].yCm - pts[i].yCm
            totalDist += sqrt(dx * dx + dy * dy)
            totalTimeS += (pts[i + 1].tMs - pts[i].tMs) / 1000f
        }
        return if (totalTimeS > 0) totalDist / totalTimeS else 0f
    }

    /** Indica si hay una sesión de grabación actualmente en curso. */
    val isActive: Boolean get() = isRecording

    /** Número de puntos registrados en la sesión actual. */
    val pointCount: Int get() = points.size
}