package com.example.philab.data.repository

import com.example.philab.data.local.dao.SessionDao
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: SessionDao) {

    // Guardar

    /**
     * Persiste una sesión completa (metadatos + puntos).
     *
     * @param experimentName Nombre del experimento elegido por el usuario.
     * @param editedLabel    Nombre del objeto detectado, editado por el usuario.
     */
    suspend fun saveSession(
        results: ExperimentResults,
        experimentName: String,
        editedLabel: String
    ): Long {
        val entity = SessionEntity(
            experimentName  = experimentName,
            selectedLabel   = editedLabel,
            recordedAt      = results.recordedAt,
            durationMs      = results.durationMs,
            sampleCount     = results.sampleCount,
            sampleRateHz    = results.sampleRateHz,
            unit            = results.unit,
            cmPerPx         = results.cmPerPx,
            totalDistanceCm = results.totalDistanceCm,
            displacementCm  = results.displacementCm,
            avgSpeedCmS     = results.avgSpeedCmS,
            avgAccelCmS2    = results.avgAccelCmS2,
            minX            = results.minX,
            maxX            = results.maxX,
            minT            = results.minT,
            maxT            = results.maxT
        )
        val sessionId = dao.insertSession(entity)

        val points = results.points.mapIndexed { index, dp ->
            PointEntity(
                idSession = sessionId,
                index     = index,
                tSeconds  = dp.tSeconds,
                xCm       = dp.xCm,
                yCm       = dp.yCm
            )
        }
        dao.insertPoints(points)

        return sessionId
    }

    // Consultar

    fun getAllSessions(): Flow<List<SessionEntity>> = dao.getAllSessions()

    suspend fun getFullResults(sessionId: Long): ExperimentResults? {
        val session = dao.getSessionById(sessionId) ?: return null
        val pointEntities = dao.getPointsBySession(sessionId)

        val points = pointEntities.map { pe ->
            DataPoint(
                tMs = (pe.tSeconds * 1000).toLong(),
                xCm = pe.xCm,
                yCm = pe.yCm
            )
        }

        return ExperimentResults(
            points          = points,
            unit            = session.unit,
            selectedLabel   = session.selectedLabel,
            durationMs      = session.durationMs,
            sampleCount     = session.sampleCount,
            sampleRateHz    = session.sampleRateHz,
            cmPerPx         = session.cmPerPx,
            recordedAt      = session.recordedAt,
            totalDistanceCm = session.totalDistanceCm,
            displacementCm  = session.displacementCm,
            avgSpeedCmS     = session.avgSpeedCmS,
            avgAccelCmS2    = session.avgAccelCmS2,
            minX            = session.minX,
            maxX            = session.maxX,
            minT            = session.minT,
            maxT            = session.maxT
        )
    }

    //Eliminar / Renombrar

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun renameSession(id: Long, name: String) = dao.renameSession(id, name)
}