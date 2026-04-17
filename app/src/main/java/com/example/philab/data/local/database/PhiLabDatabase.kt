package com.example.philab.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.philab.data.local.dao.SessionDao
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base de datos principal de la aplicación PhiLab utilizando Room.
 *
 * Define las entidades persistentes y provee acceso al DAO correspondiente.
 */
@Database(
    entities = [SessionEntity::class, PointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhiLabDatabase : RoomDatabase() {

    /**
     * Proporciona acceso a las operaciones de base de datos relacionadas con sesiones.
     *
     * @return Instancia de [SessionDao].
     */
    abstract fun sessionDao(): SessionDao

    companion object {

        @Volatile
        private var INSTANCE: PhiLabDatabase? = null

        /**
         * Obtiene la instancia única de la base de datos.
         *
         * Implementa el patrón Singleton para asegurar una sola instancia
         * durante el ciclo de vida de la aplicación.
         *
         * @param context Contexto de la aplicación.
         * @return Instancia de [PhiLabDatabase].
         */
        fun getInstance(context: Context): PhiLabDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhiLabDatabase::class.java,
                    "philab_db"
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
    }

    /**
     * Callback ejecutado durante la creación inicial de la base de datos.
     *
     * Permite poblar la base de datos con datos de ejemplo.
     */
    private class SeedCallback : Callback() {

        /**
         * Se ejecuta cuando la base de datos es creada por primera vez.
         *
         * Inserta una sesión de demostración junto con sus puntos asociados.
         *
         * @param db Base de datos SQLite subyacente.
         */
        override fun onCreate(db: SupportSQLiteDatabase) {
            // onCreate solo se llama la PRIMERA. onOpen para testear
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.sessionDao()
                    val sessionId = dao.insertSession(DatabaseSeeder.createDemoSession())
                    dao.insertPoints(DatabaseSeeder.createDemoPoints(sessionId))
                }
            }
        }
    }
}