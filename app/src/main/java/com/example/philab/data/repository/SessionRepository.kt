package com.example.philab.data.repository

import com.example.philab.data.local.dao.SessionDao
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import com.example.philab.domain.experiment.DataPoint
import com.example.philab.domain.experiment.ExperimentResults
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio encargado de la gestión de sesiones de experimentos.
 *
 * Proporciona métodos para:
 * - Persistir sesiones y sus puntos asociados.
 * - Consultar sesiones almacenadas.
 * - Reconstruir resultados completos a partir de la base de datos.
 * - Eliminar o renombrar sesiones.
 *
 * @param dao Objeto de acceso a datos para operaciones de base de datos.
 */
class SessionRepository(private val dao: SessionDao) {

    /**
     * Persiste una sesión completa incluyendo sus metadatos y puntos registrados.
     *
     * @param results Resultados del experimento a almacenar.
     * @param experimentName Nombre del experimento definido por el usuario.
     * @param editedLabel Nombre del objeto detectado, posiblemente editado.
     * @return ID de la sesión creada en la base de datos.
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

    /**
     * Obtiene todas las sesiones almacenadas como flujo reactivo.
     *
     * @return Flujo de listas de [SessionEntity].
     */
    fun getAllSessions(): Flow<List<SessionEntity>> = dao.getAllSessions()

    /**
     * Reconstruye los resultados completos de una sesión a partir de la base de datos.
     *
     * @param sessionId Identificador de la sesión.
     * @return Objeto [ExperimentResults] o null si no existe la sesión.
     */
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

    /**
     * Elimina una sesión por su identificador.
     *
     * @param id ID de la sesión a eliminar.
     */
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    /**
     * Renombra una sesión existente.
     *
     * @param id ID de la sesión.
     * @param name Nuevo nombre del experimento.
     */
    suspend fun renameSession(id: Long, name: String) = dao.renameSession(id, name)
}