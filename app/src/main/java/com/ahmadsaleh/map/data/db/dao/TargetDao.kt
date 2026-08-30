package com.ahmadsaleh.map.data.db.dao

import androidx.room.*
import com.ahmadsaleh.map.data.db.entity.TargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets ORDER BY sortOrder ASC")
    suspend fun getAll(): List<TargetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: TargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(targets: List<TargetEntity>)

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM targets")
    suspend fun deleteAll()
}
