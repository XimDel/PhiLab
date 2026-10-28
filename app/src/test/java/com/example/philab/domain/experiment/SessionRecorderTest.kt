package com.example.philab.domain.experiment

import com.example.philab.domain.SyntheticTrajectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SessionRecorderTest {

    @Test
    fun cameraTimestamps_inOtherTimeBase_startAtZero() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0) { t -> 10.0 * t }
        val results = SyntheticTrajectory.record(pts, cameraBaseNs = 9_999_000_000_000L)

        assertEquals(0L, results.points.first().tMs)
        assertEquals(pts.last().tMs - pts.first().tMs, results.durationMs)
    }

    @Test
    fun slowMotion_distanceIsNotDiscarded() {
        // A 3 cm/s y 30 fps cada paso mide 0.1 cm: el umbral anterior de 0.15 cm por
        // segmento descartaba todo el recorrido.
        val pts = SyntheticTrajectory.points(durationSec = 3.0, noiseCm = 0.05) { t -> 3.0 * t }
        val results = SyntheticTrajectory.record(pts)

        assertTrue("distancia ${results.totalDistanceCm}", abs(results.totalDistanceCm - 9f) < 0.5f)
        assertTrue("velocidad ${results.avgSpeedCmS}", abs(results.avgSpeedCmS - 3f) < 0.15f)
    }

    @Test
    fun acceleration_isNotUnderestimated() {
        val pts = SyntheticTrajectory.points(durationSec = 1.5) { t -> 0.5 * 40.0 * t * t }
        val results = SyntheticTrajectory.record(pts)

        assertTrue("aceleración ${results.avgAccelCmS2}", abs(results.avgAccelCmS2 - 40f) < 2f)
        assertTrue("desplazamiento ${results.displacementCm}", abs(results.displacementCm - 45f) < 1f)
    }

    @Test
    fun classifier_usesFit() {
        val mru = SyntheticTrajectory.record(SyntheticTrajectory.points(durationSec = 2.0) { t -> 20.0 * t })
        val mrua = SyntheticTrajectory.record(SyntheticTrajectory.points(durationSec = 1.5) { t -> 20.0 * t * t })

        assertEquals("Movimiento Rectilíneo Uniforme (MRU)", MotionClassifier.classify(mru))
        assertEquals("Movimiento Rectilíneo Uniformemente Acelerado (MRUA)", MotionClassifier.classify(mrua))
    }
}
