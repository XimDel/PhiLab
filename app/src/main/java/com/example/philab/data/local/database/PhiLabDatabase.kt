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

@Database(
    entities = [SessionEntity::class, PointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhiLabDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: PhiLabDatabase? = null

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

    private class SeedCallback : Callback() {
        // onCreate solo se llama la PRIMERA. onOpen para testear
        override fun onCreate(db: SupportSQLiteDatabase) {
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