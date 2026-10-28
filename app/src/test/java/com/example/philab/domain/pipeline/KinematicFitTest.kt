package com.example.philab.domain.pipeline

import com.example.philab.domain.SyntheticTrajectory
import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.pipeline.KinematicFit.MotionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class KinematicFitTest {

    private fun assertWithin(expected: Double, actual: Float, relTol: Double, what: String) {
        val err = abs(actual - expected) / abs(expected)
        assertTrue("$what: esperado $expected, obtenido $actual (error ${"%.2f".format(err * 100)} %)", err <= relTol)
    }

    @Test
    fun mru_horizontal_recoversVelocity() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0) { t -> 20.0 * t }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.UNIFORM, fit.motionType)
        assertWithin(20.0, fit.meanVelocity, 0.02, "velocidad")
        assertWithin(40.0, fit.distance, 0.03, "distancia")
        assertTrue("|a| debe ser compatible con 0", abs(fit.acceleration) < 3 * fit.accelerationStdErr + 1f)
    }

    @Test
    fun mrua_fromRest_recoversAcceleration() {
        val pts = SyntheticTrajectory.points(durationSec = 1.5) { t -> 0.5 * 40.0 * t * t }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.UNIFORMLY_ACCELERATED, fit.motionType)
        assertWithin(40.0, fit.acceleration, 0.05, "aceleración")
        assertWithin(40.0 * 1.5 / 2, fit.meanVelocity, 0.03, "velocidad media")
    }

    @Test
    fun freeFall_vertical_recoversGravity() {
        // Caída libre en el eje Y de la imagen: el pipeline anterior solo miraba X.
        val pts = SyntheticTrajectory.points(durationSec = 0.45, angleDeg = 90.0) { t -> 0.5 * 978.0 * t * t }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.UNIFORMLY_ACCELERATED, fit.motionType)
        assertWithin(978.0, fit.acceleration, 0.05, "g")
        assertTrue("el eje debe ser vertical", abs(fit.axisY) > 0.99f)
    }

    @Test
    fun inclinedPlane_recoversAccelerationAlongSlope() {
        val pts = SyntheticTrajectory.points(durationSec = 1.5, angleDeg = 30.0) { t -> 5.0 * t + 0.5 * 25.0 * t * t }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.UNIFORMLY_ACCELERATED, fit.motionType)
        assertWithin(25.0, fit.acceleration, 0.05, "aceleración en el plano")
        assertWithin(5.0, fit.initialVelocity, 0.15, "velocidad inicial")
    }

    @Test
    fun braking_givesNegativeAcceleration() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0) { t -> 40.0 * t - 0.5 * 15.0 * t * t }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.UNIFORMLY_ACCELERATED, fit.motionType)
        assertWithin(-15.0, fit.acceleration, 0.05, "desaceleración")
    }

    @Test
    fun outliers_areRejected() {
        val clean = SyntheticTrajectory.points(durationSec = 2.0) { t -> 20.0 * t }
        val corrupted = clean.mapIndexed { i, p ->
            if (i % 15 == 7) DataPoint(p.tMs, p.xCm + 30f, p.yCm - 25f) else p
        }
        val fit = KinematicFit.fit(corrupted)

        assertEquals(MotionType.UNIFORM, fit.motionType)
        assertWithin(20.0, fit.meanVelocity, 0.02, "velocidad con outliers")
        assertTrue("debe descartar los saltos", fit.usedPoints <= fit.totalPoints - 4)
    }

    @Test
    fun noiseOnly_isStationary() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0, noiseCm = 0.5) { 0.0 }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.STATIONARY, fit.motionType)
    }

    @Test
    fun backAndForth_isNonUniform_withFullPathDistance() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0, noiseCm = 0.2) { t -> 10.0 * sin(PI * t) }
        val fit = KinematicFit.fit(pts)

        assertEquals(MotionType.NON_UNIFORM, fit.motionType)
        assertWithin(40.0, fit.distance, 0.10, "distancia de ida y vuelta")
    }

    @Test
    fun uncertainty_coversTrueValue() {
        // En 20 repeticiones con distinto ruido, el valor real debe caer casi siempre en ±3σ.
        val misses = (1..20).count { seed ->
            val pts = SyntheticTrajectory.points(durationSec = 1.5, seed = seed.toLong()) { t -> 0.5 * 40.0 * t * t }
            val fit = KinematicFit.fit(pts)
            abs(fit.acceleration - 40f) > 3 * fit.accelerationStdErr
        }
        assertTrue("el valor real quedó fuera de ±3σ en $misses de 20 casos", misses <= 1)
    }
}
