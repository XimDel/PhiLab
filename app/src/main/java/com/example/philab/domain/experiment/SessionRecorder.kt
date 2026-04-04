package com.example.philab.domain.experiment

import kotlin.math.abs
import kotlin.math.roundToInt

class SessionRecorder {

    private val points = mutableListOf<DataPoint>()
    private var startTimeNs  = 0L   // nanosegundos del sensor — solo para tMs relativo de puntos
    private var startEpochMs = 0L   // epoch ms real — para recordedAt de la sesión en Room
    private var isRecording = false
    private var currentLabel = "Object"
    private var currentCmPerPx = 1f
    private var currentUnit = "px"

    /**
     * Inicia una nueva sesión.
     *
     * @param startFrameTimestampNs  Timestamp en nanosegundos del sensor de cámara
     *                               (ImageProxy.imageInfo.timestamp).
     *                               Se usa SOLO para calcular tMs relativo de cada punto.
     *                               Si es 0 se usa System.nanoTime() como fallback.
     *
     * IMPORTANTE: startFrameTimestampNs NO es epoch — es tiempo desde el arranque
     * del dispositivo. Por eso recordedAt usa System.currentTimeMillis() por separado.
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
        startEpochMs = System.currentTimeMillis()   // epoch real para recordedAt
        currentLabel = label
        currentCmPerPx = cmPerPx
        currentUnit = unit
        isRecording = true
    }

    fun updateMetadata(cmPerPx: Float, unit: String) {
        if (!isRecording) return
        currentCmPerPx = cmPerPx
        currentUnit = unit
    }

    /**
     * Registra un punto de posición.
     *
     * @param xCm                  Posición horizontal en cm (o px sin calibración).
     * @param yCm                  Posición vertical en cm (o px sin calibración).
     * @param frameTimestampNs     Timestamp del frame en nanosegundos del sensor
     *                             (ImageProxy.imageInfo.timestamp).
     *                             Si es 0 se usa System.nanoTime() como fallback.
     *
     * FIX timestamp: usar el timestamp del sensor del frame en lugar de
     * System.currentTimeMillis() elimina el jitter causado por la latencia
     * variable del pipeline de análisis (~5–40 ms por frame).
     * El timestamp del sensor es monotónico con jitter < 1 ms.
     */
    fun addPoint(xCm: Float, yCm: Float, frameTimestampNs: Long = 0L) {
        if (!isRecording) return
        val tsNs = if (frameTimestampNs > 0L) frameTimestampNs else System.nanoTime()
        val tMs = (tsNs - startTimeNs) / 1_000_000L   // ns → ms
        points.add(DataPoint(tMs = tMs.coerceAtLeast(0L), xCm = xCm, yCm = yCm))
    }

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
            distanciaTotal += abs(points[i].xCm - points[i - 1].xCm)
        }

        val desplazamiento = points.last().xCm - points.first().xCm
        val velocidadMedia = if (durationS > 0) desplazamiento / durationS else 0f

        val aceleracionMedia = if (points.size >= 3) {
            val dt0 = (points[1].tMs - points[0].tMs) / 1000f
            val dt1 = (points.last().tMs - points[points.size - 2].tMs) / 1000f
            val vInicial = if (dt0 > 0) (points[1].xCm - points[0].xCm) / dt0 else 0f
            val vFinal   = if (dt1 > 0) (points.last().xCm - points[points.size - 2].xCm) / dt1 else 0f
            if (durationS > 0) (vFinal - vInicial) / durationS else 0f
        } else 0f

        return ExperimentResults(
            points           = points.toList(),
            unit             = currentUnit,
            selectedLabel    = currentLabel,
            recordedAt       = startEpochMs,   // epoch ms real — correcto para UI e historial
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

    val isActive: Boolean get() = isRecording
    val pointCount: Int get() = points.size
}