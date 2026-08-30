package com.example.my_cannon.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.my_cannon.data.model.CannonPosition
import com.example.my_cannon.data.model.GeoPoint
import com.example.my_cannon.data.model.UtmPoint

@Entity(tableName = "cannon")
data class CannonEntity(
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
    val utmZoneLetter: String
) {
    fun toModel() = CannonPosition(
        id = id,
        name = name,
        description = description,
        elevation = elevation,
        geoPoint = GeoPoint(latitude, longitude, altitude),
        utmPoint = UtmPoint(utmEasting, utmNorthing, utmZoneNumber, utmZoneLetter.firstOrNull() ?: 'N')
    )

    companion object {
        fun fromModel(m: CannonPosition) = CannonEntity(
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
            utmZoneLetter = m.utmPoint.zoneLetter.toString()
        )
    }
}
