package com.ahmadsaleh.map.data.model

data class CannonPosition(
    val id: String = "cannon_1",
    val name: String = "مربض المدفعية الرئيسي",
    val description: String = "",
    val elevation: Double = 0.0,
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
