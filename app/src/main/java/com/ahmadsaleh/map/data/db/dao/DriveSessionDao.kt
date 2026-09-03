package com.ahmadsaleh.map.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmadsaleh.map.data.db.entity.DriveSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriveSession(session: DriveSessionEntity): Long

    @Query("SELECT * FROM drive_sessions ORDER BY startTime DESC")
    fun getAllDriveSessionsFlow(): Flow<List<DriveSessionEntity>>

    @Query("SELECT * FROM drive_sessions ORDER BY startTime DESC")
    suspend fun getAllDriveSessionsList(): List<DriveSessionEntity>

    @Delete
    suspend fun deleteDriveSession(session: DriveSessionEntity)

    @Query("DELETE FROM drive_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM drive_sessions")
    suspend fun clearAll()
}
