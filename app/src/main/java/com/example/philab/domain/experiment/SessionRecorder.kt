package com.example.philab.domain.experiment

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Clase encargada de registrar una sesión de experimento en tiempo real.
 *
 * Acumula puntos de medición, calcula métricas cinemáticas al finalizar
 * y construye un objeto [ExperimentResults] con los datos procesados.
 * El ciclo de vida típico es: [start] → [addPoint] (N veces) → [stop].
 */
class SessionRecorder {

    private val points = mutableListOf<DataPoint>()
    private var startTimeNs  = 0L
    private var startEpochMs = 0L
    private var isRecording = false
    private var currentLabel = "Object"
    private var currentCmPerPx = 1f
    private var currentUnit = "px"

    /**
     * Inicia una nueva sesión de grabación, descartando cualquier dato previo.
     *
     * El parámetro [startFrameTimestampNs] debe ser el timestamp del sensor de cámara
     * (`ImageProxy.imageInfo.timestamp`), que es monotónico desde el arranque del dispositivo
     * y se usa exclusivamente para calcular el tiempo relativo de cada punto.
     * La fecha real de la sesión se registra por separado con `System.currentTimeMillis()`.
     *
     * @param label Etiqueta del objeto que se va a rastrear durante la sesión.
     * @param cmPerPx Factor de escala inicial en centímetros por píxel.
     * @param unit Unidad de medida a usar en los resultados (`"cm"` o `"px"`).
     * @param startFrameTimestampNs Timestamp en nanosegundos del primer fotograma,
     *   tomado del sensor de cámara. Si es `0` se usa `System.nanoTime()` como alternativa.
     */
    fun start(
        label: String,
        cmPerPx: Float,
        unit: String,
        startFrameTimestampNs: Long = 0L
    ) {
        points.clear()
        startTimeNs  = if (startFrameTimestampNs > 0L) startFrameTimestampNs
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
     * El tiempo relativo del punto se calcula a partir del timestamp del sensor de cámara
     * para evitar el jitter de hasta 40 ms que introduciría `System.currentTimeMillis()`.
     * No tiene efecto si no hay una sesión en curso.
     *
     * @param xCm Posición horizontal en centímetros, o en píxeles si no hay calibración activa.
     * @param yCm Posición vertical en centímetros, o en píxeles si no hay calibración activa.
     * @param frameTimestampNs Timestamp en nanosegundos del fotograma actual, tomado del sensor
     *   de cámara.
     */
    fun addPoint(xCm: Float, yCm: Float, frameTimestampNs: Long = 0L) {
        if (!isRecording) return
        val tsNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        val tMs = (tsNs - startTimeNs) / 1_000_000L
        points.add(DataPoint(tMs = tMs.coerceAtLeast(0L), xCm = xCm, yCm = yCm))
    }

    /**
     * Detiene la sesión activa, calcula las métricas cinemáticas y devuelve los resultados.
     *
     * Las métricas calculadas incluyen: distancia total recorrida, desplazamiento neto,
     * velocidad media, aceleración media estimada a partir del primer y último intervalo,
     * duración total y frecuencia de muestreo.
     *
     * @return [ExperimentResults] con todos los puntos y métricas calculadas,
     *   o `null` si la sesión no estaba activa, tiene menos de dos puntos
     *   o su duración es cero o negativa.
     */
    fun stop(): ExperimentResults? {
        if (!isRecording) return null
        isRecording = false

        if (points.size < 2) return null

        val durationMs = points.last().tMs - points.first().tMs
        if (durationMs <= 0) return null

        val durationS = durationMs / 1000f
        val sampleRateHz = points.size / durationS

        var distanciaTotal = 0f
        for (i in 1 until points.size) {
            val dx = points[i].xCm - points[i - 1].xCm
            val dy = points[i].yCm - points[i - 1].yCm
            distanciaTotal += kotlin.math.sqrt(dx * dx + dy * dy)
        }

        val dxNet = points.last().xCm - points.first().xCm
        val dyNet = points.last().yCm - points.first().yCm
        val desplazamiento = kotlin.math.sqrt(dxNet * dxNet + dyNet * dyNet)
        val velocidadMedia = if (durationS > 0) distanciaTotal / durationS else 0f

        val aceleracionMedia = if (points.size >= 3) {
            val dt0 = (points[1].tMs - points[0].tMs) / 1000f
            val dt1 = (points.last().tMs - points[points.size - 2].tMs) / 1000f
            val vInicial = if (dt0 > 0) kotlin.math.sqrt(
                (points[1].xCm - points[0].xCm).pow(2) + (points[1].yCm - points[0].yCm).pow(2)
            ) / dt0 else 0f
            val vFinal = if (dt1 > 0) kotlin.math.sqrt(
                (points.last().xCm - points[points.size-2].xCm).pow(2) +
                        (points.last().yCm - points[points.size-2].yCm).pow(2)
            ) / dt1 else 0f
            if (durationS > 0) (vFinal - vInicial) / durationS else 0f
        } else 0f

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

    /** Indica si hay una sesión de grabación actualmente en curso. */
    val isActive: Boolean get() = isRecording

    /** Número de puntos registrados en la sesión actual. */
    val pointCount: Int get() = points.size
}