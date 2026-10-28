package com.example.philab.domain

import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults
import com.example.philab.domain.experiment.SessionRecorder
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Genera trayectorias sintéticas con valores cinemáticos conocidos para validar
 * el pipeline contra la "realidad" exacta.
 *
 * Simula lo que entrega la cámara: muestreo a [fps] con jitter de tiempo, ruido
 * gaussiano en la posición medida (en ambos ejes) y timestamps redondeados a ms.
 */
object SyntheticTrajectory {

    /**
     * @param durationSec Duración del registro.
     * @param fps Frecuencia de muestreo nominal.
     * @param noiseCm Desviación estándar del ruido de posición.
     * @param angleDeg Dirección del movimiento en la imagen (0 = derecha, 90 = abajo).
     * @param timeJitterMs Desviación estándar del jitter de los timestamps.
     * @param seed Semilla para que los tests sean reproducibles.
     * @param position Posición real sobre la línea del movimiento en función del tiempo.
     */
    fun points(
        durationSec: Double,
        fps: Double = 30.0,
        noiseCm: Double = 0.3,
        angleDeg: Double = 0.0,
        timeJitterMs: Double = 3.0,
        seed: Long = 42L,
        position: (Double) -> Double
    ): List<DataPoint> {
        val rnd = Random(seed)
        val ux = cos(Math.toRadians(angleDeg))
        val uy = sin(Math.toRadians(angleDeg))
        val x0 = 20.0
        val y0 = 30.0
        val n = (durationSec * fps).toInt() + 1
        var lastMs = -1L
        return (0 until n).mapNotNull { i ->
            val tTrue = i / fps + rnd.nextGaussian() * timeJitterMs / 1000.0
            val tMs = Math.round(tTrue.coerceAtLeast(0.0) * 1000.0)
            if (tMs <= lastMs) return@mapNotNull null
            lastMs = tMs
            val t = tMs / 1000.0
            val s = position(t)
            DataPoint(
                tMs = tMs,
                xCm = (x0 + s * ux + rnd.nextGaussian() * noiseCm).toFloat(),
                yCm = (y0 + s * uy + rnd.nextGaussian() * noiseCm).toFloat()
            )
        }
    }

    /**
     * Pasa los puntos por [SessionRecorder] usando timestamps de cámara en una base
     * de tiempo distinta a `System.nanoTime()`, como ocurre en muchos dispositivos.
     */
    fun record(points: List<DataPoint>, cameraBaseNs: Long = 7_000_000_000_000L): ExperimentResults {
        val recorder = SessionRecorder()
        recorder.start(label = "test", cmPerPx = 0.1f, unit = "cm")
        for (p in points) {
            recorder.addPoint(p.xCm, p.yCm, frameTimestampNs = cameraBaseNs + p.tMs * 1_000_000L)
        }
        return requireNotNull(recorder.stop())
    }
}
