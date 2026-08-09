package com.example.my_cannon.data.model

data class CannonPosition(
    val id: String = "cannon_1",
    val name: String = "مربط المدفعية الرئيسي",
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
