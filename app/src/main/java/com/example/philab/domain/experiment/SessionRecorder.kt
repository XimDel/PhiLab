package com.example.philab.domain.experiment

import com.example.philab.domain.pipeline.KinematicFit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Registra una sesión de experimento de tracking en tiempo real.
 *
 * Acumula los puntos de posición medidos durante la grabación y, al finalizar con
 * [stop], calcula las métricas cinemáticas (distancia, desplazamiento, velocidad
 * media y aceleración) ajustando los modelos físicos con [KinematicFit].
 *
 * Los puntos se guardan crudos: cualquier suavizado previo al ajuste introduce
 * retraso y sesga la aceleración.
 *
 * Ciclo de vida: [start] → [addPoint] (N veces) → [stop].
 */
class SessionRecorder {

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
     *   tomado del sensor de cámara. Si es `0`, el primer punto registrado define el origen.
     */
    fun start(
        label: String,
        cmPerPx: Float,
        unit: String,
        startFrameTimestampNs: Long = 0L
    ) {
        points.clear()
        startTimeNs = startFrameTimestampNs.coerceAtLeast(0L)
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
     * Si la sesión se inició sin timestamp de cámara, el primer punto define el origen
     * temporal: el reloj del sensor no siempre comparte base con `System.nanoTime()`.
     * No tiene efecto si no hay una sesión en curso.
     *
     * @param xCm Posición horizontal en centímetros, o en píxeles si no hay calibración.
     * @param yCm Posición vertical en centímetros, o en píxeles si no hay calibración.
     * @param frameTimestampNs Timestamp en nanosegundos del fotograma, del sensor de cámara.
     */
    fun addPoint(xCm: Float, yCm: Float, frameTimestampNs: Long = 0L) {
        if (!isRecording) return
        val tsNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        if (startTimeNs == 0L) startTimeNs = tsNs
        val tMs = (tsNs - startTimeNs) / 1_000_000L
        points.add(DataPoint(tMs = tMs.coerceAtLeast(0L), xCm = xCm, yCm = yCm))
    }

    /**
     * Detiene la sesión y calcula las métricas cinemáticas con [KinematicFit].
     *
     * Las métricas salen del ajuste por mínimos cuadrados de los modelos MRU/MRUA sobre
     * el eje del movimiento, no de diferencias entre puntos consecutivos. Los puntos
     * crudos originales se preservan en el resultado para gráficos y exportación.
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

        val durationS = durationMs / 1000f
        val sampleRateHz = points.size / durationS

        val fit = KinematicFit.fit(points)

        return ExperimentResults(
            points           = points.toList(),
            unit             = currentUnit,
            selectedLabel    = currentLabel,
            recordedAt       = startEpochMs,
            durationMs       = durationMs,
            sampleCount      = points.size,
            sampleRateHz     = (sampleRateHz * 10).roundToInt() / 10f,
            cmPerPx          = currentCmPerPx,
            totalDistanceCm  = fit.distance,
            displacementCm   = abs(fit.displacement),
            avgSpeedCmS      = abs(fit.meanVelocity),
            avgAccelCmS2     = fit.acceleration,
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