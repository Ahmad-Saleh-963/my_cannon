package com.ahmadsaleh.map.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmadsaleh.map.data.model.GeoPoint
import com.ahmadsaleh.map.data.model.ReferencePoint
import com.ahmadsaleh.map.data.model.UtmPoint

@Entity(tableName = "reference_points")
data class ReferencePointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val elevation: Double,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val utmEasting: Double,
    val utmNorthing: Double,
    val utmZoneNumber: Int,
    val utmZoneLetter: String,
    val sortOrder: Long = System.currentTimeMillis()
) {
    fun toModel() = ReferencePoint(
        id = id,
        name = name,
        description = description,
        elevation = elevation,
        geoPoint = GeoPoint(latitude, longitude, altitude),
        utmPoint = UtmPoint(utmEasting, utmNorthing, utmZoneNumber, utmZoneLetter.firstOrNull() ?: 'N')
    )

    companion object {
        fun fromModel(m: ReferencePoint, order: Long = System.currentTimeMillis()) = ReferencePointEntity(
            id = m.id,
            name = m.name,
            description = m.description,
            elevation = m.elevation,
            latitude = m.geoPoint.latitude,
            longitude = m.geoPoint.longitude,
            altitude = m.geoPoint.altitude,
            utmEasting = m.utmPoint.easting,
            utmNorthing = m.utmPoint.northing,
            utmZoneNumber = m.utmPoint.zoneNumber,
            utmZoneLetter = m.utmPoint.zoneLetter.toString(),
            sortOrder = order
        )
    }
}
