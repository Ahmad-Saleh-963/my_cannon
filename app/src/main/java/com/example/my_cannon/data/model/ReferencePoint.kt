package com.example.my_cannon.data.model

data class ReferencePoint(
    val id: String,
    val name: String,
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
