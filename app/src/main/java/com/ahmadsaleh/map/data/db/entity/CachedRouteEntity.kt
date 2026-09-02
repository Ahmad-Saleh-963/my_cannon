package com.ahmadsaleh.map.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_routes")
data class CachedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startLat: Double,
    val startLon: Double,
    val destLat: Double,
    val destLon: Double,
    val geoJsonGeometry: String,
    val durationMinutes: Int,
    val distanceKm: Double,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
