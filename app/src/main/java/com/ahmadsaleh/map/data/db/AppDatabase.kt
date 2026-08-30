package com.ahmadsaleh.map.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ahmadsaleh.map.data.db.dao.CannonDao
import com.ahmadsaleh.map.data.db.dao.ReferencePointDao
import com.ahmadsaleh.map.data.db.dao.TargetDao
import com.ahmadsaleh.map.data.db.entity.CannonEntity
import com.ahmadsaleh.map.data.db.entity.ReferencePointEntity
import com.ahmadsaleh.map.data.db.entity.TargetEntity

@Database(
    entities = [CannonEntity::class, TargetEntity::class, ReferencePointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cannonDao(): CannonDao
    abstract fun targetDao(): TargetDao
    abstract fun referencePointDao(): ReferencePointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cannon_mission.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
