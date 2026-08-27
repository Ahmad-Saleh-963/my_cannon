package com.example.my_cannon.data.model

data class ReferencePoint(
    val id: String,
    val name: String,
    val description: String = "",
    val elevation: Double = 0.0,
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
