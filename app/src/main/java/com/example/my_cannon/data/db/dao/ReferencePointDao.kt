package com.example.my_cannon.data.db.dao

import androidx.room.*
import com.example.my_cannon.data.db.entity.ReferencePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferencePointDao {
    @Query("SELECT * FROM reference_points ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ReferencePointEntity>>

    @Query("SELECT * FROM reference_points ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ReferencePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: ReferencePointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(refs: List<ReferencePointEntity>)

    @Query("DELETE FROM reference_points WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reference_points")
    suspend fun deleteAll()
}
