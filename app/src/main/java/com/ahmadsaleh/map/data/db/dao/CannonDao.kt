package com.ahmadsaleh.map.data.db.dao

import androidx.room.*
import com.ahmadsaleh.map.data.db.entity.CannonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CannonDao {
    @Query("SELECT * FROM cannon LIMIT 1")
    fun observeCannon(): Flow<CannonEntity?>

    @Query("SELECT * FROM cannon LIMIT 1")
    suspend fun getCannon(): CannonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cannon: CannonEntity)

    @Query("DELETE FROM cannon")
    suspend fun deleteAll()
}
