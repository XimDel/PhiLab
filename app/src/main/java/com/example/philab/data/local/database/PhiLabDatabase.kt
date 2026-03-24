package com.example.philab.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.philab.data.local.dao.SessionDao
import com.example.philab.data.local.entity.PointEntity
import com.example.philab.data.local.entity.SessionEntity

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
                ).build().also { INSTANCE = it }
            }
    }
}