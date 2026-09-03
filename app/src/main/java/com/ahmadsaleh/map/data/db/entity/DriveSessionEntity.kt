package com.ahmadsaleh.map.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drive_sessions")
data class DriveSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val startLat: Double,
    val startLon: Double,
    val startPlaceName: String,
    val endLat: Double,
    val endLon: Double,
    val endPlaceName: String,
    val topSpeedKmh: Double,
    val averageSpeedKmh: Double,
    val distanceKm: Double,
    val geoJsonGeometry: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
