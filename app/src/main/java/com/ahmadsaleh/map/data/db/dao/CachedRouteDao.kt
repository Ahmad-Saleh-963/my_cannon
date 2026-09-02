package com.ahmadsaleh.map.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmadsaleh.map.data.db.entity.CachedRouteEntity

@Dao
interface CachedRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: CachedRouteEntity)

    @Query("SELECT * FROM cached_routes ORDER BY timestamp DESC")
    suspend fun getAllCachedRoutes(): List<CachedRouteEntity>

    @Query("DELETE FROM cached_routes WHERE abs(startLat - :sLat) < 0.001 AND abs(startLon - :sLon) < 0.001 AND abs(destLat - :dLat) < 0.001 AND abs(destLon - :dLon) < 0.001")
    suspend fun deleteExactPair(sLat: Double, sLon: Double, dLat: Double, dLon: Double)
}
