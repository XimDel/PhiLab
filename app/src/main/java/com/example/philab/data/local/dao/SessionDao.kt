package com.example.philab.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para la gestión de sesiones y sus puntos asociados.
 *
 * Define las operaciones de inserción, consulta, actualización y eliminación
 * sobre las entidades [SessionEntity] y [PointEntity] en la base de datos local.
 */
@Dao
interface SessionDao {

    /**
     * Inserta una nueva sesión en la base de datos.
     *
     * Si existe un conflicto, la sesión será reemplazada.
     *
     * @param session Entidad de la sesión a insertar.
     * @return ID generado para la sesión insertada.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    /**
     * Inserta una lista de puntos asociados a una sesión.
     *
     * Si existe un conflicto, los puntos serán reemplazados.
     *
     * @param points Lista de entidades [PointEntity] a insertar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PointEntity>)

    /**
     * Obtiene todas las sesiones registradas, ordenadas de más reciente a más antigua.
     *
     * @return Flujo reactivo con la lista de sesiones.
     */
    @Query("SELECT * FROM sessions ORDER BY recordedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /**
     * Obtiene una sesión específica a partir de su ID.
     *
     * @param id Identificador de la sesión.
     * @return La sesión correspondiente o null si no existe.
     */
    @Query("SELECT * FROM sessions WHERE idSession = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    /**
     * Obtiene todos los puntos asociados a una sesión, ordenados cronológicamente.
     *
     * @param id Identificador de la sesión.
     * @return Lista de puntos pertenecientes a la sesión.
     */
    @Query("SELECT * FROM points WHERE idSession = :id ORDER BY `index` ASC")
    suspend fun getPointsBySession(id: Long): List<PointEntity>

    /**
     * Obtiene el mayor ID de sesión registrado hasta el momento.
     *
     * Incluye valores eliminados debido al comportamiento de autoincremento
     * en SQLite. Si no hay registros, retorna 0.
     *
     * @return El ID máximo de sesión o 0 si la tabla está vacía.
     */
    @Query("SELECT COALESCE(MAX(idSession), 0) FROM sessions")
    suspend fun getMaxSessionId(): Long

    /**
     * Elimina una sesión por su ID.
     *
     * Los puntos asociados se eliminan en cascada.
     *
     * @param id Identificador de la sesión a eliminar.
     */
    @Query("DELETE FROM sessions WHERE idSession = :id")
    suspend fun deleteSession(id: Long)

    /**
     * Actualiza el nombre de una sesión existente.
     *
     * @param id Identificador de la sesión.
     * @param name Nuevo nombre del experimento.
     */
    @Query("UPDATE sessions SET experimentName = :name WHERE idSession = :id")
    suspend fun renameSession(id: Long, name: String)
}