package com.example.philab.domain.experiment

import kotlin.math.abs
import kotlin.math.roundToInt

class SessionRecorder {

    private val points = mutableListOf<DataPoint>()
    private var startTimeMs = 0L
    private var isRecording = false
    private var currentLabel = "Object"
    private var currentCmPerPx = 1f
    private var currentUnit = "px"

    fun start(label: String, cmPerPx: Float, unit: String) {
        points.clear()
        startTimeMs = System.currentTimeMillis()
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

    fun addPoint(xCm: Float, yCm: Float) {
        if (!isRecording) return
        val tMs = System.currentTimeMillis() - startTimeMs
        points.add(DataPoint(tMs = tMs, xCm = xCm, yCm = yCm))
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
            val vFinal  = if (dt1 > 0) (points.last().xCm - points[points.size - 2].xCm) / dt1 else 0f
            if (durationS > 0) (vFinal - vInicial) / durationS else 0f
        } else 0f

        return ExperimentResults(
            points        = points.toList(),
            unit          = currentUnit,
            selectedLabel = currentLabel,
            recordedAt    = startTimeMs,
            durationMs    = durationMs,
            sampleCount   = points.size,
            sampleRateHz  = (sampleRateHz * 10).roundToInt() / 10f,
            cmPerPx       = currentCmPerPx,
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