package com.example.my_cannon.data.model

data class CannonPosition(
    val id: String = "cannon_1",
    val name: String = "مربط المدفعية الرئيسي",
    val description: String = "",
    val elevation: Double = 0.0,
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
