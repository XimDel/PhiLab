package com.example.philab.data.repository

import com.example.philab.data.local.dao.SessionDao
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val dao: SessionDao) {

    // ── Guardar ───────────────────────────────────────────────────────────────

    /**
     * Persiste una sesión completa (metadatos + puntos) en una sola transacción.
     * @param editedName Nombre editado por el usuario en SessionSummaryDialog.
     * @return ID de la sesión recién insertada.
     */
    suspend fun saveSession(results: ExperimentResults, editedName: String): Long {
        val entity = SessionEntity(
            experimentName   = editedName,
            selectedLabel    = results.selectedLabel,
            recordedAt       = results.recordedAt,
            durationMs       = results.durationMs,
            sampleCount      = results.sampleCount,
            sampleRateHz     = results.sampleRateHz,
            unit             = results.unit,
            cmPerPx          = results.cmPerPx,
            totalDistanceCm  = results.totalDistanceCm,
            displacementCm   = results.displacementCm,
            avgSpeedCmS      = results.avgSpeedCmS,
            avgAccelCmS2     = results.avgAccelCmS2,
            minX             = results.minX,
            maxX             = results.maxX,
            minT             = results.minT,
            maxT             = results.maxT
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

    // ── Consultar ─────────────────────────────────────────────────────────────

    /** Flow con la lista de sesiones para el historial (más reciente primero). */
    fun getAllSessions(): Flow<List<SessionEntity>> = dao.getAllSessions()

    /**
     * Reconstruye un ExperimentResults completo desde la BD.
     * Útil para abrir ResultsScreen desde el historial.
     */
    suspend fun getFullResults(sessionId: Long): ExperimentResults? {
        val session = dao.getSessionById(sessionId) ?: return null
        val pointEntities = dao.getPointsBySession(sessionId)

        val points = pointEntities.map { pe ->
            DataPoint(
                tMs  = (pe.tSeconds * 1000).toLong(),
                xCm  = pe.xCm,
                yCm  = pe.yCm
            )
        }

        return ExperimentResults(
            points           = points,
            unit             = session.unit,
            selectedLabel    = session.experimentName,   // usa el nombre editado
            durationMs       = session.durationMs,
            sampleCount      = session.sampleCount,
            sampleRateHz     = session.sampleRateHz,
            cmPerPx          = session.cmPerPx,
            recordedAt       = session.recordedAt,
            totalDistanceCm  = session.totalDistanceCm,
            displacementCm   = session.displacementCm,
            avgSpeedCmS      = session.avgSpeedCmS,
            avgAccelCmS2     = session.avgAccelCmS2,
            minX             = session.minX,
            maxX             = session.maxX,
            minT             = session.minT,
            maxT             = session.maxT
        )
    }

    // ── Eliminar / Renombrar ──────────────────────────────────────────────────

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun renameSession(id: Long, name: String) = dao.renameSession(id, name)
}