package com.example.philab.domain.pipeline

import com.example.philab.domain.SyntheticTrajectory
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class KinematicPipelineTest {

    private fun median(values: List<Float>): Float = values.sorted()[values.size / 2]

    @Test
    fun mrua_withoutNoise_derivativesAreExact() {
        val a = 40.0
        val v0 = 5.0
        val pts = SyntheticTrajectory.points(durationSec = 1.5, noiseCm = 0.0, timeJitterMs = 4.0) { t ->
            v0 * t + 0.5 * a * t * t
        }
        val motion = KinematicPipeline.process(SyntheticTrajectory.record(pts)).motion

        for (p in motion) {
            val expectedV = v0 + a * p.tSec
            assertTrue("v(${p.tSec}) = ${p.velocity}, esperado $expectedV", abs(p.velocity - expectedV) < 0.02 * expectedV + 0.3)
            assertTrue("a(${p.tSec}) = ${p.acceleration}, esperado $a", abs(p.acceleration - a) < 0.03 * a)
        }
    }

    @Test
    fun positionChart_startsAtZero_andGrowsAlongMotion() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0, angleDeg = 180.0) { t -> 15.0 * t }
        val chart = KinematicPipeline.process(SyntheticTrajectory.record(pts)).chart

        assertTrue("posición inicial ${chart.position.first().second}", abs(chart.position.first().second) < 1f)
        assertTrue("posición final ${chart.position.last().second}", abs(chart.position.last().second - 30f) < 1.5f)
    }

    @Test
    fun verticalMotion_hasVelocityAndAcceleration() {
        // Antes solo se derivaba X: una caída libre daba velocidad y aceleración ~0.
        val pts = SyntheticTrajectory.points(durationSec = 0.5, angleDeg = 90.0) { t -> 0.5 * 978.0 * t * t }
        val motion = KinematicPipeline.process(SyntheticTrajectory.record(pts)).motion

        val medianAccel = median(motion.map { it.acceleration })
        assertTrue("aceleración mediana $medianAccel", abs(medianAccel - 978f) < 0.10f * 978f)
    }

    @Test
    fun noisyMrua_accelerationChartIsCentredOnTrueValue() {
        val pts = SyntheticTrajectory.points(durationSec = 2.0, noiseCm = 0.3) { t -> 0.5 * 30.0 * t * t }
        val motion = KinematicPipeline.process(SyntheticTrajectory.record(pts)).motion

        val medianAccel = median(motion.map { it.acceleration })
        assertTrue("aceleración mediana $medianAccel", abs(medianAccel - 30f) < 0.10f * 30f)
    }
}
