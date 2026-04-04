package com.example.philab.data.local.database

import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity

object DatabaseSeeder {

    fun createDemoSession(): SessionEntity = SessionEntity(
        idSession = 0,
        experimentName = "Experimento Demo",
        selectedLabel = "pelota",
        recordedAt = System.currentTimeMillis(),
        durationMs = 3043L,
        sampleCount = 71,
        sampleRateHz = 23f,
        unit = "cm",
        cmPerPx = 0.058f,
        totalDistanceCm = 34.21f,
        displacementCm = 34.21f,
        avgSpeedCmS = 11.24f,
        avgAccelCmS2 = 1.79f,
        minX = 0f,
        maxX = 34.21f,
        minT = 0f,
        maxT = 3.043f
    )

    fun createDemoPoints(sessionId: Long): List<PointEntity> {
        val count = 71
        val dt = 1f / 23f
        val v0 = 8.5f
        val a  = 1.8f

        return List(count) { i ->
            val t = i * dt
            val x = (v0 * t + 0.5f * a * t * t).coerceAtMost(35.6f)
            PointEntity(
                id = 0,
                idSession = sessionId,
                index = i,
                tSeconds = t,
                xCm = x,
                yCm = 0f
            )
        }
    }
}