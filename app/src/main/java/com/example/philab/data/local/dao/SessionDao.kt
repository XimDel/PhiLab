package com.example.philab.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    // Insertar

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PointEntity>)

    // Consultar

    /** Lista de sesiones para el historial, ordenadas de más reciente a más antigua. */
    @Query("SELECT * FROM sessions ORDER BY recordedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /** Una sesión por ID. */
    @Query("SELECT * FROM sessions WHERE idSession = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    /** Puntos de una sesión, en orden cronológico. */
    @Query("SELECT * FROM points WHERE idSession = :id ORDER BY `index` ASC")
    suspend fun getPointsBySession(id: Long): List<PointEntity>

    /**
     * Retorna el mayor idSession registrado hasta ahora (incluyendo eliminados,
     * ya que sqlite_sequence guarda el último autoincrement usado).
     * Devuelve 0 si la tabla está vacía.
     */
    @Query("SELECT COALESCE(MAX(idSession), 0) FROM sessions")
    suspend fun getMaxSessionId(): Long

    // Eliminar

    /** Elimina la sesión (los puntos se borran en cascada). */
    @Query("DELETE FROM sessions WHERE idSession = :id")
    suspend fun deleteSession(id: Long)

    // Renombrar

    @Query("UPDATE sessions SET experimentName = :name WHERE idSession = :id")
    suspend fun renameSession(id: Long, name: String)
}